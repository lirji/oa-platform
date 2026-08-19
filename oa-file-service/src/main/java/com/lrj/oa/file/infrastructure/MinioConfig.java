package com.lrj.oa.file.infrastructure;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MinIO 客户端。桶不存在时自动创建 —— 首次部署不该还要人去控制台点一下。 */
@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    public MinioClient minioClient(@Value("${oa.file.endpoint:http://localhost:39000}") String endpoint,
                                   @Value("${oa.file.access-key:oaminio}") String accessKey,
                                   @Value("${oa.file.secret-key:oaminio_dev_pwd}") String secretKey,
                                   @Value("${oa.file.bucket:oa-files}") String bucket) {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint).credentials(accessKey, secretKey).build();
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建对象存储桶 {}", bucket);
            }
        } catch (Exception e) {
            // 不让启动失败：MinIO 不可达时文件功能不可用，但组织/权限/审批不该跟着起不来。
            // 上传时会再报一次明确的错，那时用户正在等结果，是提示的正确时机。
            log.warn("对象存储不可达（{}）：文件上传下载将不可用。{}", endpoint, e.toString());
        }
        return client;
    }
}
