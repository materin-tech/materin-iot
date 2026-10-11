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
package com.materin.gateway.connector;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.materin.gateway.core.ConnectorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * mqtt-bridge 上行转发器：子 broker 消息 → 平台遥测（含首见子设备 connect 声明）。
 * JSON payload 直接作为遥测键值；非 JSON 降级为 {"payload": "原始文本"}。
 */
@Slf4j
public class MqttBridgeForwarder {

    private final String id;
    private final ConnectorContext ctx;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> seenDevices = ConcurrentHashMap.newKeySet();

    public MqttBridgeForwarder(String id, ConnectorContext ctx) {
        this.id = id;
        this.ctx = ctx;
    }

    /** 处理一条子 broker 消息：提取 deviceKey →（首见 connect）→ telemetry */
    public void forward(BridgeTopicMapping mapping, Mqtt3Publish publish) {
        try {
            String topic = publish.getTopic().toString();
            String deviceKey = mapping.extractDeviceKey(topic);
            if (deviceKey == null || deviceKey.isBlank()) {
                log.debug("[{}] topic {} 提取不到 deviceKey，忽略", id, topic);
                return;
            }
            if (seenDevices.add(deviceKey)) {
                ctx.connect(deviceKey, deviceKey, mapping.productKey());
            }
            ctx.telemetry(deviceKey, parsePayload(publish.getPayloadAsBytes()));
        } catch (Exception e) {
            log.warn("[{}] 遥测转发失败: {}", id, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(byte[] body) {
        try {
            Object parsed = mapper.readValue(body, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of("payload", new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Map.of("payload", new String(body, StandardCharsets.UTF_8));
        }
    }
}
