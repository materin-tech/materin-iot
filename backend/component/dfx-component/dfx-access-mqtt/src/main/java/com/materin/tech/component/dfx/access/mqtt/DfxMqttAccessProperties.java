package com.materin.tech.component.dfx.access.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** DFX MQTT 接入配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "materin.dfx.access.mqtt")
public class DfxMqttAccessProperties {

    private boolean enabled = true;
    private String host = "127.0.0.1";
    private int port = 1883;
    private String username = "materin-svc-consumer";
    private String password = "materin-svc-dev-password";
    /** 共享订阅组（与主遥测链路分组隔离，互不影响负载均衡） */
    private String shareGroup = "materin-dfx";
    /** 在线标记 TTL（秒），dfx 上行同样刷新设备在线滑动窗口 */
    private int onlineTtlSeconds = 300;
}
