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
package com.materin.gateway.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.materin.gateway.config.GatewayProperties;
import com.materin.gateway.connector.DemoConnector;
import com.materin.gateway.connector.MqttBridgeConnector;
import com.materin.gateway.connector.modbus.ModbusTcpConnector;
import com.materin.gateway.connector.opcua.OpcUaConnector;
import lombok.extern.slf4j.Slf4j;

/**
 * connector 注册与生命周期管理：按配置实例化（type 分发），逐个启动，失败跳过。
 */
@Slf4j
@Component
public class ConnectorRegistry {

    /** 未知 connector 类型时的清晰报错提示 */
    private static final String SUPPORTED_TYPES = "demo, mqtt-bridge, modbus-tcp, opcua";

    private final GatewayProperties properties;
    private final List<Connector> started = new ArrayList<>();

    public ConnectorRegistry(GatewayProperties properties) {
        this.properties = properties;
    }

    /** 按配置启动全部 connector；单个失败只记日志并跳过，不影响其他 connector */
    public void startAll(ConnectorContext ctx) {
        List<Map<String, Object>> configs = properties.getConnectors();
        if (configs == null || configs.isEmpty()) {
            log.warn("未配置任何 connector（materin.gateway.connectors），网关将空转");
            return;
        }
        for (Map<String, Object> cfg : configs) {
            String type = cfg.get("type") == null ? "" : String.valueOf(cfg.get("type")).trim();
            String id = cfg.get("id") == null ? type : String.valueOf(cfg.get("id"));
            try {
                log.debug("connector 配置: type={}, cfg={}", type, cfg);
                Connector connector = create(type, cfg);
                connector.start(ctx);
                started.add(connector);
                log.info("connector [{}] (type={}) 已启动", id, type);
            } catch (Exception e) {
                log.error("connector [{}] (type={}) 启动失败，已跳过: {}", id, type, e.getMessage());
            }
        }
    }

    /** 按 type 实例化 connector，未知类型清晰报错 */
    private Connector create(String type, Map<String, Object> cfg) {
        return switch (type) {
            case "demo" -> new DemoConnector(cfg);
            case "mqtt-bridge" -> new MqttBridgeConnector(cfg);
            case "modbus-tcp" -> new ModbusTcpConnector(cfg);
            case "opcua" -> new OpcUaConnector(cfg);
            default -> throw new IllegalArgumentException(
                    "未知 connector 类型 '" + type + "'，当前支持: " + SUPPORTED_TYPES);
        };
    }

    /** 停止全部已启动的 connector，单个失败不影响其余 */
    public void stopAll() {
        for (Connector connector : started) {
            try {
                connector.stop();
            } catch (Exception e) {
                log.warn("connector [{}] 停止失败: {}", connector.id(), e.getMessage());
            }
        }
        started.clear();
    }

    /** 当前已启动的 connector 列表（不可变视图） */
    public List<Connector> all() {
        return List.copyOf(started);
    }
}
