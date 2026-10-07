package com.materin.tech.component.storage.minio;

import com.materin.tech.component.storage.StorageClient;
import com.materin.tech.component.storage.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * StorageClient 的 MinIO 实现（storage-minio）。
 * 首次使用懒建桶；预签名 URL 供设备侧固件直连下载。
 */
@Slf4j
public class MinioStorageClient implements StorageClient {

    private final MinioClientHolder holder;

    public MinioStorageClient(MinioClientHolder holder) {
        this.holder = holder;
    }

    private io.minio.MinioClient client() {
        return holder.client();
    }

    @Override
    public String putObject(String bucket, String key, InputStream in, long size, String contentType) {
        try (InputStream stream = in) {
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(stream, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("MinIO putObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream getObject(String bucket, String key) {
        try {
            return client().getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO getObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try {
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO deleteObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public StorageStat statObject(String bucket, String key) {
        try {
            StatObjectResponse s = client().statObject(
                    StatObjectArgs.builder().bucket(bucket).object(key).build());
            return new StorageStat(s.size(), s.etag(), s.contentType());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO statObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String key, int expireSeconds) {
        try {
            return holder.external().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket).object(key)
                    .expiry((int) Math.max(60, expireSeconds), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO presignedGetUrl 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    /** MinIO 客户端持有者（懒初始化 + 懒建桶）。 */
    @Slf4j
    @Component
    @ConditionalOnProperty(prefix = "materin.storage", name = "type", havingValue = "MINIO")
    public static class MinioClientHolder {

        private final StorageProperties properties;
        private volatile io.minio.MinioClient client;
        private volatile io.minio.MinioClient externalClient;

        public MinioClientHolder(StorageProperties properties) {
            this.properties = properties;
        }

        /** 预签名用外部端点客户端（未配置 externalEndpoint 时与内部一致）。 */
        public io.minio.MinioClient external() {
            String ext = properties.getExternalEndpoint();
            if (ext == null || ext.isBlank()) {
                return client();
            }
            if (externalClient == null) {
                synchronized (this) {
                    if (externalClient == null) {
                        externalClient = io.minio.MinioClient.builder()
                                .endpoint(ext)
                                .credentials(properties.getAccessKey(), properties.getSecretKey())
                                .build();
                    }
                }
            }
            return externalClient;
        }

        public io.minio.MinioClient client() {
            if (client == null) {
                synchronized (this) {
                    if (client == null) {
                        client = io.minio.MinioClient.builder()
                                .endpoint(properties.getEndpoint())
                                .credentials(properties.getAccessKey(), properties.getSecretKey())
                                .build();
                        try {
                            String bucket = properties.getBucket();
                            if (!client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                                client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                                log.info("MinIO 桶已创建: {}", bucket);
                            }
                        } catch (Exception e) {
                            log.warn("MinIO 建桶检查失败（首次使用时重试）: {}", e.getMessage());
                        }
                    }
                }
            }
            return client;
        }
    }

    /** 装配：materin.storage.type=MINIO 时生效。 */
    @Configuration
    @ConditionalOnProperty(prefix = "materin.storage", name = "type", havingValue = "MINIO")
    public static class MinioConfiguration {

        @Bean
        public StorageClient minioStorageClient(MinioClientHolder holder) {
            return new MinioStorageClient(holder);
        }
    }
}
