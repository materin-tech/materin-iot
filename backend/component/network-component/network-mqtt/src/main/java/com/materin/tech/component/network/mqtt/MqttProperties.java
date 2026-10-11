/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
