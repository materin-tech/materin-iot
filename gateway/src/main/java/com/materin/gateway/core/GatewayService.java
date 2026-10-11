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

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.materin.gateway.config.GatewayProperties;
import com.materin.gateway.mqtt.GatewayTopics;
import com.materin.gateway.mqtt.PlatformMqttClient;
import com.materin.gateway.mqtt.UplinkPublisher;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关核心：SmartLifecycle，启动时连接平台 MQTT 并启动全部 connector。
 * 实现 ConnectorContext 组装上行 payload；下行解析与分发委托 {@link DownlinkRouter}。
 */
@Slf4j
@Component
public class GatewayService implements SmartLifecycle, ConnectorContext {

    /** 命令回执主题缓存 TTL：5 分钟 */
    static final long REPLY_TTL_MILLIS = 5 * 60 * 1000L;

    private final GatewayProperties properties;
    private final PlatformMqttClient mqtt;
    private final UplinkPublisher publisher;
    private final ConnectorRegistry registry;
    private final ReplyTopicCache replyTopics = new ReplyTopicCache(REPLY_TTL_MILLIS);
    private final DownlinkRouter router;
    private volatile boolean running;

    public GatewayService(GatewayProperties properties, PlatformMqttClient mqtt, UplinkPublisher publisher,
            ConnectorRegistry registry) {
        this.properties = properties;
        this.mqtt = mqtt;
        this.publisher = publisher;
        this.registry = registry;
        this.router = new DownlinkRouter(registry, replyTopics);
    }

    @Override
    public void start() {
        try {
            mqtt.start(this::handleDownlink);
            registry.startAll(this);
            running = true;
            log.info("网关已启动，deviceKey={}", properties.getPlatform().getDeviceKey());
        } catch (Exception e) {
            log.error("网关启动初始化失败（进程保持运行，平台连接自动重试）: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        running = false;
        registry.stopAll();
        mqtt.stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void handleDownlink(byte[] body) { router.route(body); }

    /** 子设备上线声明（productKey 可选：声明子设备归属独立产品） */
    @Override
    public void connect(String deviceKey, String name, String productKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("device", deviceKey);
        if (name != null && !name.isBlank()) {
            payload.put("name", name);
        }
        if (productKey != null && !productKey.isBlank()) {
            payload.put("productKey", productKey);
        }
        publisher.publish(GatewayTopics.CONNECT, payload);
    }

    @Override
    public void disconnect(String deviceKey) {
        publisher.publish(GatewayTopics.DISCONNECT, Map.of("device", deviceKey));
    }

    @Override
    public void telemetry(String deviceKey, Map<String, Object> values) {
        telemetry(deviceKey, values, System.currentTimeMillis());
    }

    @Override
    public void telemetry(String deviceKey, Map<String, Object> values, long ts) {
        Map<String, Object> frame = new HashMap<>();
        frame.put("ts", ts);
        frame.put("values", values == null ? Map.of() : values);
        Map<String, Object> payload = new HashMap<>();
        payload.put(deviceKey, java.util.List.of(frame));
        publisher.publish(GatewayTopics.TELEMETRY, payload);
    }

    @Override
    public void attributes(String deviceKey, Map<String, Object> values) {
        publisher.publish(GatewayTopics.ATTRIBUTES, Map.of(deviceKey, values == null ? Map.of() : values));
    }

    @Override
    public void reply(String deviceKey, String requestId, Map<String, Object> data) {
        String topic = replyTopics.get(requestId);
        if (topic == null) {
            log.warn("回执被丢弃：requestId={} 无对应 replyTopic（未登记或已过期）", requestId);
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        // 透传下行命令携带的 traceId（MDC 不跨线程，traceId 缓存在 ReplyTopicCache 中）
        Object traceId = replyTopics.traceIdOf(requestId);
        if (traceId != null) {
            payload.put(TraceUtil.KEY, traceId);
        }
        if (data != null) {
            payload.put("data", data);
        }
        publisher.publishDirect(topic, payload);
    }

    public int connectorCount() {
        return registry.all().size();
    }
}
