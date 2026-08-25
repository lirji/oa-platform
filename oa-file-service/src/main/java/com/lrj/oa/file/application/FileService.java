package com.lrj.oa.file.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.file.infrastructure.mapper.FileObjectMapper;
import com.lrj.oa.security.annotation.ObjectScope;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传 / 下载 / 预签名。
 *
 * <p><b>object_key 用 UUID 而不是业务 id。</b>
 * 用 {@code kb/123.pdf} 这种可枚举的 key，拿到一个链接就能推出别人的；
 * 而预签名 URL 一旦生成就<b>绕过了应用层的全部判权</b>——它是直连对象存储的。
 * 所以 key 必须不可猜，预签名必须短时效，且只在<b>判权通过后</b>才签发。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final io.minio.MinioClient minio;
    private final FileObjectMapper mapper;
    private final String bucket;
    private final long maxBytes;
    private final int presignSeconds;

    public FileService(io.minio.MinioClient minio, FileObjectMapper mapper,
                       @Value("${oa.file.bucket:oa-files}") String bucket,
                       @Value("${oa.file.max-bytes:52428800}") long maxBytes,
                       @Value("${oa.file.presign-seconds:300}") int presignSeconds) {
        this.minio = minio;
        this.mapper = mapper;
        this.bucket = bucket;
        this.maxBytes = maxBytes;
        this.presignSeconds = presignSeconds;
    }

    @Transactional
    @ObjectScope(permission = "oa:file:upload", tables = "oa_sys.file_object",
            strategy = ObjectScope.Strategy.OWNER, reason = "上传元数据 owner 固定为当前用户")
    public Map<String, Object> upload(MultipartFile file, String bizType, String bizId) {
        UserContext ctx = UserContextHolder.require();
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > maxBytes) {
            throw BusinessException.of(ResultCode.BAD_REQUEST,
                    "文件超过上限 " + (maxBytes / 1024 / 1024) + " MB");
        }
        // 不可枚举的 key。日期前缀只是为了在控制台里好翻，不承担任何安全作用。
        String key = "%s/%s".formatted(java.time.LocalDate.now(), UUID.randomUUID());
        String sha;
        try (InputStream in = file.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            sha = sha256(bytes);
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new java.io.ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(file.getContentType() == null
                            ? "application/octet-stream" : file.getContentType())
                    .build());
        } catch (Exception e) {
            throw BusinessException.of(ResultCode.DEPENDENCY_UNAVAILABLE, "对象存储写入失败：" + e);
        }
        Long id = mapper.insert(TenantContext.get(), key, bucket, file.getOriginalFilename(),
                file.getContentType(), file.getSize(), sha, bizType, bizId, ctx.userId(),
                ctx.primaryOrgId(), ctx.primaryOrgPath());
        return Map.of("id", id == null ? 0L : id, "objectKey", key,
                "size", file.getSize(), "sha256", sha);
    }

    /** 元数据 + 判权。下载与预签名都必须先过这里。 */
    private FileObjectMapper.FileRow requireAccessible(long fileId) {
        UserContext ctx = UserContextHolder.require();
        FileObjectMapper.FileRow f = mapper.selectById(TenantContext.get(), fileId);
        if (f == null) throw BusinessException.of(ResultCode.NOT_FOUND, "文件不存在");
        String owner = f.ownerId;
        // 首版规则：上传者本人可读。
        // ★ 刻意保守：文件的可见范围应当跟随它所属的业务对象（知识库文档的共享、
        //   审批单的参与人），那需要跨模块问业务侧。在那条链路接通之前，
        //   宁可"权限太紧"也不能默认放开 —— 放开一个文件下载接口就是一次数据泄露。
        if (!owner.equals(ctx.userId())) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "你没有这个文件的访问权限");
        }
        return f;
    }

    public record Download(String fileName, String contentType, byte[] content) {}

    @ObjectScope(permission = "oa:file:read", tables = "oa_sys.file_object",
            strategy = ObjectScope.Strategy.OWNER, reason = "下载前校验文件 owner")
    public Download download(long fileId) {
        FileObjectMapper.FileRow f = requireAccessible(fileId);
        try (InputStream in = minio.getObject(GetObjectArgs.builder()
                .bucket(f.bucket).object(f.objectKey).build())) {
            return new Download(f.fileName,
                    f.contentType == null ? "application/octet-stream" : f.contentType,
                    in.readAllBytes());
        } catch (Exception e) {
            throw BusinessException.of(ResultCode.DEPENDENCY_UNAVAILABLE, "对象存储读取失败：" + e);
        }
    }

    /**
     * 预签名下载 URL。
     *
     * <p>★ 只在判权通过后签发，且<b>短时效</b>（默认 5 分钟）。
     * 预签名 URL 是直连对象存储的，签出去之后应用层再也管不着 ——
     * 它就是一张"凭票入场"的票，票的有效期就是泄露窗口。
     */
    @ObjectScope(permission = "oa:file:read", tables = "oa_sys.file_object",
            strategy = ObjectScope.Strategy.OWNER, reason = "预签名签发前校验文件 owner")
    public String presign(long fileId) {
        FileObjectMapper.FileRow f = requireAccessible(fileId);
        try {
            return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(f.bucket)
                    .object(f.objectKey)
                    .expiry(presignSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw BusinessException.of(ResultCode.DEPENDENCY_UNAVAILABLE, "预签名失败：" + e);
        }
    }

    @Transactional
    @ObjectScope(permission = "oa:file:upload", tables = "oa_sys.file_object",
            strategy = ObjectScope.Strategy.OWNER, reason = "删除前校验文件 owner 且 DELETE 重复带 owner 条件")
    public void delete(long fileId) {
        FileObjectMapper.FileRow f = requireAccessible(fileId);
        String userId = UserContextHolder.require().userId();
        try {
            minio.removeObject(RemoveObjectArgs.builder()
                    .bucket(f.bucket).object(f.objectKey).build());
        } catch (Exception e) {
            log.warn("对象删除失败（元数据仍会删除，可能留下孤儿对象）：{}", e.toString());
        }
        if (mapper.deleteOwn(TenantContext.get(), fileId, userId) == 0) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "文件不存在或不属于当前用户");
        }
    }

    @ObjectScope(permission = "oa:file:read", tables = "oa_sys.file_object",
            strategy = ObjectScope.Strategy.OWNER, reason = "列表 SQL 固定 owner_id 为当前用户")
    public List<Map<String, Object>> myFiles(int limit) {
        UserContext ctx = UserContextHolder.require();
        return mapper.selectOwn(TenantContext.get(), ctx.userId(), Math.min(Math.max(limit, 1), 200))
                .stream().map(f -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", f.id);
                    row.put("file_name", f.fileName);
                    row.put("content_type", f.contentType);
                    row.put("size_bytes", f.sizeBytes);
                    row.put("sha256", f.sha256);
                    row.put("biz_type", f.bizType);
                    row.put("biz_id", f.bizId);
                    row.put("created_at", f.createdAt);
                    return row;
                }).toList();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
