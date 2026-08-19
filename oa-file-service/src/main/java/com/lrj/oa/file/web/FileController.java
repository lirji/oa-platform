package com.lrj.oa.file.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.file.application.FileService;
import com.lrj.oa.security.annotation.PublicApi;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 文件服务 REST :8402。 */
@RestController
@RequestMapping("/api/v1/file")
public class FileController {

    private final FileService files;

    public FileController(FileService files) { this.files = files; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPerm("oa:file:upload")
    public Result<Map<String, Object>> upload(@RequestPart("file") MultipartFile file,
                                              @RequestParam(required = false) String bizType,
                                              @RequestParam(required = false) String bizId) {
        return Result.ok(files.upload(file, bizType, bizId));
    }

    /**
     * 直接下载（走应用层，每次都判权）。
     *
     * <p>文件名用 {@code ContentDisposition} 构造而不是手拼 header：
     * 中文文件名必须按 RFC 5987 编码，手拼出来的 `filename="中文.pdf"` 在多数浏览器上会乱码或被截断。
     */
    @GetMapping("/{id}/download")
    @RequiresPerm("oa:file:read")
    public ResponseEntity<ByteArrayResource> download(@PathVariable long id) {
        FileService.Download d = files.download(id);
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(d.fileName() == null ? "file" : d.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(
                        d.contentType() == null ? "application/octet-stream" : d.contentType()))
                .body(new ByteArrayResource(d.content()));
    }

    /** 预签名 URL：判权通过后签发，短时效。适合大文件与图片预览，不占应用带宽。 */
    @GetMapping("/{id}/presign")
    @RequiresPerm("oa:file:read")
    public Result<Map<String, String>> presign(@PathVariable long id) {
        return Result.ok(Map.of("url", files.presign(id)));
    }

    @GetMapping("/mine")
    @RequiresPerm("oa:file:read")
    public Result<List<Map<String, Object>>> mine(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(files.myFiles(limit));
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("oa:file:upload")
    public Result<Void> delete(@PathVariable long id) { files.delete(id); return Result.ok(); }

    @GetMapping("/ping")
    @PublicApi(reason = "存活探针，不返回业务数据；容器 healthcheck 与冒烟用")
    public Result<String> ping() { return Result.ok("file-ok"); }
}
