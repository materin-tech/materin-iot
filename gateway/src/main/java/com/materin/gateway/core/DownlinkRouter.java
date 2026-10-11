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
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.gateway.mqtt.GatewayTopics;
import lombok.extern.slf4j.Slf4j;

/**
 * 下行消息处理：解析 materin/gateway/downlink payload
 * {"device","requestId","data","replyTopic"} → 登记 replyTopic 缓存 → 按 deviceKey 分发到 connector。
 */
@Slf4j
public class DownlinkRouter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorRegistry registry;
    private final ReplyTopicCache replyTopics;

    public DownlinkRouter(ConnectorRegistry registry, ReplyTopicCache replyTopics) {
        this.registry = registry;
        this.replyTopics = replyTopics;
    }

    /** GatewayService 下行入口：解析 payload、登记缓存并分发命令（报文 traceId 进 MDC，分发完清理） */
    @SuppressWarnings("unchecked")
    public void route(byte[] body) {
        try {
            Map<String, Object> msg = mapper.readValue(body, Map.class);
            String device = str(msg.get("device"));
            String requestId = str(msg.get("requestId"));
            String traceId = str(msg.get(TraceUtil.KEY));
            Map<String, Object> data = (Map<String, Object>) msg.getOrDefault("data", Map.of());
            String replyTopic = str(msg.get("replyTopic"));
            if (device == null) {
                log.warn("下行消息缺少 device 字段，忽略");
                return;
            }
            if (requestId != null && replyTopic != null) {
                replyTopics.put(requestId, replyTopic, traceId);
            }
            TraceUtil.set(traceId);
            try {
                for (Connector connector : routeTargets(device)) {
                    dispatch(connector, device, data, requestId);
                }
            } finally {
                TraceUtil.clear();
            }
        } catch (Exception e) {
            log.error("处理下行消息失败: {}", e.getMessage());
        }
    }

    /** 目标 connector：声明的子设备命中则精准分发，否则广播（connector 自行忽略不认识的 deviceKey） */
    private List<Connector> routeTargets(String device) {
        List<Connector> matched = new ArrayList<>();
        for (Connector connector : registry.all()) {
            if (connector.devices().contains(device)) {
                matched.add(connector);
            }
        }
        return matched.isEmpty() ? registry.all() : matched;
    }

    private void dispatch(Connector connector, String device, Map<String, Object> data, String requestId) {
        try {
            connector.onCommand(device, data, requestId);
        } catch (Exception e) {
            log.error("connector {} 处理命令失败: {}", connector.id(), e.getMessage());
        }
    }

    private static String str(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
