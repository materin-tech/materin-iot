package com.materin.tech.component.storage.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.SimplifiedObjectMeta;
import com.materin.tech.component.storage.StorageClient;
import com.materin.tech.component.storage.StorageProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;

/**
 * StorageClient 的阿里云 OSS 实现（storage-oss）。
 * 预签名用 generatePresignedUrl（签名绑定 endpoint host）。
 */
@Slf4j
public class OssStorageClient implements StorageClient {

    private final StorageProperties properties;
    private volatile OSS client;

    public OssStorageClient(StorageProperties properties) {
        this.properties = properties;
    }

    private OSS client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = new OSSClientBuilder().build(
                            properties.getEndpoint(),
                            properties.getAccessKey(),
                            properties.getSecretKey());
                    log.info("阿里云 OSS 客户端已初始化: {}", properties.getEndpoint());
                }
            }
        }
        return client;
    }

    @Override
    public String putObject(String bucket, String key, InputStream in, long size, String contentType) {
        try (InputStream stream = in) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            if (contentType != null) {
                metadata.setContentType(contentType);
            }
            client().putObject(bucket, key, stream, metadata);
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("OSS putObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream getObject(String bucket, String key) {
        try {
            OSSObject obj = client().getObject(new GetObjectRequest(bucket, key));
            return obj.getObjectContent();
        } catch (Exception e) {
            throw new IllegalStateException("OSS getObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try {
            client().deleteObject(bucket, key);
        } catch (Exception e) {
            throw new IllegalStateException("OSS deleteObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public StorageStat statObject(String bucket, String key) {
        try {
            SimplifiedObjectMeta meta = client().getSimplifiedObjectMeta(bucket, key);
            return new StorageStat(meta.getSize(), meta.getETag(), null);
        } catch (Exception e) {
            throw new IllegalStateException("OSS statObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String key, int expireSeconds) {
        try {
            URL url = client().generatePresignedUrl(bucket, key,
                    new Date(System.currentTimeMillis() + expireSeconds * 1000L));
            return url.toString();
        } catch (Exception e) {
            throw new IllegalStateException("OSS presignedGetUrl 失败: " + key + ", " + e.getMessage(), e);
        }
    }
}
