package com.materin.tech.component.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 对象存储配置（materin.storage.*）。type 决定装配哪个实现子模块。 */
@Data
@ConfigurationProperties(prefix = "materin.storage")
public class StorageProperties {

    public enum Type {
        NONE, MINIO
        // 后续：OBS、ALIYUN
    }

    /** 是否启用存储中间件 */
    private boolean enabled = true;

    private Type type = Type.MINIO;

    private String endpoint = "http://127.0.0.1:9000";

    /**
     * 外部端点（可选，空则同 endpoint）：预签名 URL 的 host。
     * 场景：平台容器内用 minio:9000 读写，设备侧拿到 host.docker.internal:9000
     * 之类可直达的地址（S3 签名绑定 host，不能事后替换）。
     */
    private String externalEndpoint;

    private String accessKey = "minioadmin";

    private String secretKey = "minioadmin";

    /** 默认桶（OTA 固件等） */
    private String bucket = "materin";
}
