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
package com.materin.gateway.connector.opcua;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.materin.gateway.connector.Configs;
import com.materin.gateway.core.Connector;
import com.materin.gateway.core.ConnectorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * OPC-UA connector（type: opcua）：配置驱动的节点轮询采集（会话封装见 {@link OpcUaSession}）。
 * onCommand 支持 {"writeValue": {"name": ..., "value": ...}} 写节点，先回执再执行。
 */
@Slf4j
public class OpcUaConnector implements Connector {

    private final String id;
    private final OpcUaNodeConfig config;
    private final Map<String, OpcUaNodeConfig.NodeMapping> nodesByName = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile ConnectorContext ctx;
    private OpcUaSession session;
    private ScheduledExecutorService scheduler;

    public OpcUaConnector(Map<String, Object> cfg) {
        this.id = Configs.stringOf(cfg, "id", "opcua");
        this.config = OpcUaNodeConfig.parse(cfg, id);
        for (OpcUaNodeConfig.NodeMapping node : config.nodes()) {
            nodesByName.put(node.name(), node);
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return "opcua";
    }

    @Override
    public Set<String> devices() {
        return Set.of(config.deviceKey());
    }

    @Override
    public void start(ConnectorContext ctx) {
        this.ctx = ctx;
        session = new OpcUaSession(config.endpointUrl());
        if (!session.connect()) {
            log.warn("[{}] 初始连接 {} 失败（轮询时自动重试）", id, config.endpointUrl());
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(this::pollNodes,
                config.pollPeriodMs(), config.pollPeriodMs(), TimeUnit.MILLISECONDS);
    }

    private void pollNodes() {
        try {
            if (!session.isConnected() && !session.connect()) {
                return;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (OpcUaNodeConfig.NodeMapping node : config.nodes()) {
                try {
                    Object raw = session.readValue(node.nodeId());
                    if (raw != null) {
                        values.put(node.name(), raw);
                    }
                } catch (Exception e) {
                    // 单节点失败不阻断同批其余节点
                }
            }
            if (!values.isEmpty()) {
                ctx.telemetry(config.deviceKey(), values);
            }
        } catch (Exception e) {
            // 轮询失败降级：等待下一周期
        }
    }

    @Override
    public void onCommand(String deviceKey, Map<String, Object> data, String requestId) {
        if (!config.deviceKey().equals(deviceKey)) {
            return;
        }
        try {
            if (data.get("writeValue") instanceof Map<?, ?> m) {
                writeNode(Configs.castMap(m), requestId);
            } else {
                ctx.reply(deviceKey, requestId, Map.of("error", "expected {\"writeValue\":{\"name\",\"value\"}}"));
            }
        } catch (Exception e) {
            log.warn("[{}] OPC-UA 写节点失败: {}", id, e.getMessage());
            ctx.reply(deviceKey, requestId, Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private void writeNode(Map<String, Object> cmd, String requestId) throws Exception {
        String name = Configs.stringOf(cmd, "name", null);
        OpcUaNodeConfig.NodeMapping node = name == null ? null : nodesByName.get(name);
        if (node == null) {
            ctx.reply(config.deviceKey(), requestId, Map.of("error", "unknown node name"));
            return;
        }
        session.writeValue(node.nodeId(), cmd.get("value"));
        ctx.reply(config.deviceKey(), requestId, Map.of("status", "ok"));
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        session.disconnect();
    }
}
