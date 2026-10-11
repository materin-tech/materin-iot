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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.mqtt.MqttTopics;
import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.GatewayChildRegistry;
import com.materin.tech.common.spi.TelemetrySink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 网关主题族上行处理（对齐 ThingsBoard v1/gateway/* 语义）。
 * payload 均携带 "gateway" 字段（网关 deviceKey），ACL 已保证网关主题族发布者必为 GATEWAY 类型设备。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayHandler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String GATEWAY_PATH = MqttTopics.PREFIX + "gateway/";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<GatewayChildRegistry> childRegistry;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final ObjectProvider<TelemetrySink> telemetrySink;
    private final TelemetryHandler telemetryHandler;
    private final StringRedisTemplate redis;

    /** 网关主题族统一入口（topic = materin/gateway/{kind}），报文异常或网关非法时降级拒绝。 */
    public void handle(String topic, byte[] payload) {
        Map<String, Object> body = parseMap(payload);
        if (body.isEmpty()) {
            return;
        }
        // 报文顶层 traceId 为保留键：提取进 MDC 后剔除（telemetry/attributes 按 key 遍历 body）
        Object traceId = body.remove(com.materin.tech.common.core.TraceIds.PAYLOAD_KEY);
        if (traceId != null) {
            org.slf4j.MDC.put(com.materin.tech.common.core.TraceIds.MDC_KEY,
                    com.materin.tech.common.core.TraceIds.normalize(String.valueOf(traceId)));
        }
        String gateway = (String) body.get("gateway");
        if (!StringUtils.hasText(gateway)) {
            log.warn("网关报文缺少 gateway 字段: topic={}", topic);
            return;
        }
        Long gatewayDeviceId = resolveGatewayDeviceId(gateway);
        if (gatewayDeviceId == null) {
            log.warn("网关设备不存在: gateway={}", gateway);
            return;
        }
        String kind = topic.length() > GATEWAY_PATH.length()
                ? topic.substring(GATEWAY_PATH.length()) : "";
        switch (kind) {
            case "connect" -> handleConnect(gateway, gatewayDeviceId, body);
            case "disconnect" -> handleDisconnect(body);
            case "telemetry" -> handleTelemetry(gateway, gatewayDeviceId, body);
            case "attributes" -> handleAttributes(gateway, gatewayDeviceId, body);
            default -> log.debug("忽略未知网关主题: {}", topic);
        }
    }

    /** 子设备上线声明 {"gateway","device","name"}：自动注册/归属绑定 + 置在线。 */
    private void handleConnect(String gateway, long gatewayDeviceId, Map<String, Object> body) {
        String deviceKey = (String) body.get("device");
        if (!StringUtils.hasText(deviceKey)) {
            log.warn("网关 connect 报文缺少 device 字段: gateway={}", gateway);
            return;
        }
        GatewayChildRegistry registry = childRegistry.getIfAvailable();
        if (registry == null) {
            log.warn("无 GatewayChildRegistry SPI，忽略子设备上线声明: device={}", deviceKey);
            return;
        }
        String deviceName = (String) body.get("name");
        String productKey = (String) body.get("productKey");
        try {
            registry.registerOrAttach(deviceKey, deviceName, gatewayDeviceId, productKey);
            telemetryHandler.touchOnline(deviceKey);
            log.info("子设备上线: gateway={}, device={}", gateway, deviceKey);
        } catch (Exception e) {
            log.warn("子设备上线登记失败: gateway={}, device={}, {}", gateway, deviceKey, e.getMessage());
        }
    }

    /** 子设备离线声明 {"gateway","device"}：落离线状态 + 清 Redis 在线标记。 */
    private void handleDisconnect(Map<String, Object> body) {
        String deviceKey = (String) body.get("device");
        if (!StringUtils.hasText(deviceKey)) {
            log.warn("网关 disconnect 报文缺少 device 字段");
            return;
        }
        GatewayChildRegistry registry = childRegistry.getIfAvailable();
        if (registry == null) {
            log.warn("无 GatewayChildRegistry SPI，忽略子设备离线声明: device={}", deviceKey);
            return;
        }
        try {
            registry.markOffline(deviceKey);
            redis.delete(RedisKeys.DEVICE_ONLINE + deviceKey);
            log.info("子设备离线: device={}", deviceKey);
        } catch (Exception e) {
            log.warn("子设备离线处理失败: device={}, {}", deviceKey, e.getMessage());
        }
    }

    /** 子设备遥测 {"gateway","<childKey>":[{"ts":ms,"values":{..}}]}：隐式注册 + Redis 最新值 + 时序库。 */
    private void handleTelemetry(String gatewayKey, long gatewayDeviceId, Map<String, Object> body) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String childKey = entry.getKey();
            if ("gateway".equals(childKey) || !(entry.getValue() instanceof List<?> samples)
                    || samples.isEmpty()) {
                continue;
            }
            boolean selfData = childKey.equals(gatewayKey);
            for (Object sampleObj : samples) {
                if (!(sampleObj instanceof Map<?, ?> sample)) {
                    continue;
                }
                Map<String, Object> values = asMap(sample.get("values"));
                if (values.isEmpty() || (!selfData && !ensureChild(childKey, gatewayDeviceId))) {
                    continue;
                }
                long ts = sample.get("ts") instanceof Number n ? n.longValue()
                        : System.currentTimeMillis();
                telemetryHandler.storeTelemetry(childKey, values);
                telemetryHandler.touchOnline(childKey);
                TelemetrySink sink = telemetrySink.getIfAvailable();
                if (sink != null) {
                    sink.saveTelemetry(childKey, ts, values);
                }
            }
        }
    }

    /** 子设备属性 {"gateway","<childKey>":{..}}：隐式注册 + Redis 最新值 HASH + 日志。 */
    private void handleAttributes(String gateway, long gatewayDeviceId, Map<String, Object> body) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String childKey = entry.getKey();
            if ("gateway".equals(childKey)) {
                continue;
            }
            // 网关自身属性（childKey == gateway）：仅写最新值，不作为子设备注册
            boolean selfData = childKey.equals(gateway);
            Map<String, Object> values = asMap(entry.getValue());
            if (values.isEmpty() || (!selfData && !ensureChild(childKey, gatewayDeviceId))) {
                continue;
            }
            telemetryHandler.storeTelemetry(childKey, values);
            log.info("属性上报: gateway={}, device={}, self={}, keys={}",
                    gateway, childKey, selfData, values.keySet());
        }
    }

    /** 隐式注册：telemetry/attributes 中未登记的子设备先经 SPI 注册/绑定归属。 */
    private boolean ensureChild(String childKey, long gatewayDeviceId) {
        GatewayChildRegistry registry = childRegistry.getIfAvailable();
        if (registry == null) {
            return false;
        }
        try {
            registry.registerOrAttach(childKey, null, gatewayDeviceId);
            return true;
        } catch (Exception e) {
            log.warn("子设备隐式注册失败: device={}, {}", childKey, e.getMessage());
            return false;
        }
    }

    private Long resolveGatewayDeviceId(String gateway) {
        DeviceCredentialLookup lookup = credentialLookup.getIfAvailable();
        if (lookup == null) {
            return null;
        }
        return lookup.findByKey(gateway)
                .map(DeviceCredentialLookup.DeviceCredential::deviceId)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private Map<String, Object> parseMap(byte[] payload) {
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (Exception e) {
            log.warn("网关报文解析失败: {}", e.getMessage());
            return Map.of();
        }
    }
}
