package com.materin.tech.component.network.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** MQTT 接入配置。 */
@Data
@ConfigurationProperties(prefix = "materin.network.mqtt")
public class MqttProperties {

    /** 是否启用 MQTT 接入 */
    private boolean enabled = true;

    private String host = "127.0.0.1";

    private int port = 1883;

    /** 平台服务账号（消费共享订阅），鉴权由 /mqtt/auth 中服务账号分支处理 */
    private String serviceUsername = "materin-svc-consumer";

    private String servicePassword = "materin-svc-dev-password";

    /** 设备在线滑动 TTL（秒），每次收到上报刷新 */
    private long onlineTtlSeconds = 300;
}
