package com.materin.tech.component.iotdb;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** IoTDB 接入配置。 */
@Data
@ConfigurationProperties(prefix = "materin.iotdb")
public class IotdbProperties {

    /** 是否启用 IoTDB 时序组件 */
    private boolean enabled = true;

    private String host = "127.0.0.1";

    private int port = 6667;

    private String username = "root";

    private String password = "root";

    /** 树模型数据库（按设备维度：{database}.p{productId}.d{deviceId}.测量列） */
    private String database = "root.materin";

    /** SessionPool 连接池大小 */
    private int maxPoolSize = 8;
}
