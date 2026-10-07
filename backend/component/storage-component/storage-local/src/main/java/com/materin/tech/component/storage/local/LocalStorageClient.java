package com.materin.tech.component.storage.local;

import com.materin.tech.component.storage.StorageClient;
import com.materin.tech.component.storage.StorageProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * StorageClient 的本地文件系统实现（storage-local）：
 * 不依赖任何云服务，对象落盘到 base-path/bucket/key。
 * 预签名 = 平台自带下载端点 + HMAC 签名参数（expire 后失效）。
 * 适用：单机部署 / 离线环境 / 开发环境。
 */
@Slf4j
public class LocalStorageClient implements StorageClient {

    private final StorageProperties properties;

    public LocalStorageClient(StorageProperties properties) {
        this.properties = properties;
    }

    /** 对象落盘绝对路径（bucket 为一级目录，隔离命名空间）。 */
    private Path resolve(String bucket, String key) {
        Path base = Path.of(properties.getLocal().getBasePath(), bucket).toAbsolutePath().normalize();
        Path target = base.resolve(key).normalize();
        // 路径穿越防护：解析后必须仍在 base 之下
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法对象 key: " + key);
        }
        return target;
    }

    @Override
    public String putObject(String bucket, String key, InputStream in, long size, String contentType) {
        try {
            Path target = resolve(bucket, key);
            Files.createDirectories(target.getParent());
            try (InputStream stream = in) {
                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("本地存储 putObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream getObject(String bucket, String key) {
        try {
            return Files.newInputStream(resolve(bucket, key));
        } catch (Exception e) {
            throw new IllegalStateException("本地存储 getObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try {
            Files.deleteIfExists(resolve(bucket, key));
        } catch (Exception e) {
            throw new IllegalStateException("本地存储 deleteObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public StorageStat statObject(String bucket, String key) {
        try {
            Path p = resolve(bucket, key);
            return new StorageStat(Files.size(p), null, null);
        } catch (Exception e) {
            throw new IllegalStateException("本地存储 statObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    /**
     * 预签名 = 平台下载端点 + HMAC(key+expire)。
     * public-url 必须是设备可达的平台地址（如 http://<局域网IP>:8080）。
     */
    @Override
    public String presignedGetUrl(String bucket, String key, int expireSeconds) {
        String publicUrl = properties.getLocal().getPublicUrl();
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new IllegalStateException(
                    "materin.storage.local.public-url 未配置（本地存储的预签名走平台下载端点）");
        }
        long expire = System.currentTimeMillis() + expireSeconds * 1000L;
        String sign = sign(bucket, key, expire);
        return String.format("%s/api/v1/storage/local/%s/%s?expire=%d&sign=%s",
                publicUrl.replaceAll("/$", ""), bucket, key, expire, sign);
    }

    /** 与 LocalStorageDownloadController 的校验逻辑保持一致。 */
    public static String sign(String secretKey, String bucket, String key, long expire) {
        try {
            String data = bucket + "|" + key + "|" + expire;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String sign(String bucket, String key, long expire) {
        return sign(properties.getSecretKey(), bucket, key, expire);
    }
}
