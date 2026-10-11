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
package com.materin.gateway.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 网关配置（materin.gateway.*）：平台 MQTT 连接信息 + connector 列表。
 * 所有 connector 以原始 Map 承载，由 ConnectorRegistry 按 type 分发解析。
 */
@Data
@Component
@ConfigurationProperties(prefix = "materin.gateway")
public class GatewayProperties {

    /** 平台 EMQX 连接信息 */
    private final Platform platform = new Platform();

    /** connector 配置列表，每项至少含 id / type，其余字段由具体 connector 解析 */
    private List<Map<String, Object>> connectors = new ArrayList<>();

    /** 网关自身遥测上报间隔（毫秒） */
    private long reportIntervalMs = 30_000L;

    /** 平台连接参数：网关以一台 GATEWAY 设备凭证接入（username=deviceKey, password=secret） */
    @Data
    public static class Platform {

        private String host = "127.0.0.1";

        private int port = 1883;

        /** 网关设备的 deviceKey（即 MQTT username） */
        private String deviceKey;

        /** 网关设备密钥（即 MQTT password） */
        private String secret;

        /** MQTT clientId，缺省复用 deviceKey */
        private String clientId;
    }
}
