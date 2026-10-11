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
package com.materin.gateway.mqtt;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 平台上行发布封装：自动注入 gateway 字段（gateway = 网关 deviceKey）并序列化为 JSON。
 * publishDirect 不注入 gateway 字段，用于命令回执。
 */
@Slf4j
@Component
public class UplinkPublisher {

    private final PlatformMqttClient mqtt;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UplinkPublisher(PlatformMqttClient mqtt, GatewayProperties properties) {
        this.mqtt = mqtt;
        this.properties = properties;
    }

    /** 上行发布：payload 顶层自动注入 gateway 字段与 traceId（调度线程无 MDC 时自动生成） */
    public void publish(String topic, Map<String, Object> payload) {
        Map<String, Object> full = new HashMap<>(payload);
        full.put("gateway", properties.getPlatform().getDeviceKey());
        full.put(com.materin.gateway.core.TraceUtil.KEY,
                com.materin.gateway.core.TraceUtil.current());
        log.debug("上行发布: topic={} keys={}", topic, full.keySet());
        publishDirect(topic, full);
    }

    /** 原样发布（不注入 gateway 字段），用于命令回执 */
    public void publishDirect(String topic, Map<String, Object> payload) {
        try {
            mqtt.publishRaw(topic, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            log.error("发布到 {} 失败: {}", topic, e.getMessage());
        }
    }
}
