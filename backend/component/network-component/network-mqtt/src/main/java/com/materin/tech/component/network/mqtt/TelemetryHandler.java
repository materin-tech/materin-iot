package com.materin.tech.component.network.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.mqtt.MqttTopics;
import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.TelemetrySink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 上行消息处理：
 * - report：刷新在线 TTL、写遥测最新值 HASH、经 SPI 节流更新 DB last_online、落时序库
 * - event：刷新在线 TTL、落时序库（evt_ 前缀列）
 * - reply：定向回传指令应答
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryHandler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final MqttProperties properties;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final ObjectProvider<com.materin.tech.common.spi.CommandReplySink> replySink;
    private final ObjectProvider<TelemetrySink> telemetrySink;
    private final ObjectMapper objectMapper;

    public void handle(String productKey, String deviceKey, String suffix, byte[] payload) {
        touchOnline(deviceKey);
        if (MqttTopics.UP_REPORT.equals(suffix)) {
            handleReport(deviceKey, payload);
        } else if (MqttTopics.UP_EVENT.equals(suffix)) {
            handleEvent(deviceKey, payload);
        } else if (MqttTopics.UP_REPLY.equals(suffix)) {
            deliverCommandReply(payload);
        }
    }

    /**
     * report：Redis 最新值 + 时序库历史（两者均 best-effort，时序库失败不影响上行链路）。
     */
    private void handleReport(String deviceKey, byte[] payload) {
        Map<String, Object> values = parseMap(payload);
        if (values.isEmpty()) {
            return;
        }
        storeTelemetry(deviceKey, values);
        TelemetrySink sink = telemetrySink.getIfAvailable();
        if (sink != null) {
            sink.saveTelemetry(deviceKey, System.currentTimeMillis(), values);
        }
    }

    /**
     * event：日志 + 时序库（桥接内宽容解析为 evt_ 前缀列）。
     */
    private void handleEvent(String deviceKey, byte[] payload) {
        Map<String, Object> values = parseMap(payload);
        log.info("设备事件上报: {} event={}", deviceKey, textual(payload));
        if (values.isEmpty()) {
            return;
        }
        TelemetrySink sink = telemetrySink.getIfAvailable();
        if (sink != null) {
            sink.saveEvent(deviceKey, System.currentTimeMillis(), values);
        }
    }

    private void touchOnline(String deviceKey) {
        redis.opsForValue().set(RedisKeys.DEVICE_ONLINE + deviceKey, "1",
                Duration.ofSeconds(properties.getOnlineTtlSeconds()));
        lookupDeviceId(deviceKey).ifPresent(deviceId ->
                credentialLookup.getIfAvailable().touchOnline(deviceId, LocalDateTime.now()));
    }

    private java.util.Optional<Long> lookupDeviceId(String deviceKey) {
        DeviceCredentialLookup lookup = credentialLookup.getIfAvailable();
        if (lookup == null) {
            return java.util.Optional.empty();
        }
        return lookup.findByKey(deviceKey).map(DeviceCredentialLookup.DeviceCredential::deviceId);
    }

    private void storeTelemetry(String deviceKey, Map<String, Object> values) {
        try {
            String key = RedisKeys.DEVICE_TELEMETRY + deviceKey;
            values.forEach((k, v) -> redis.opsForHash().put(key, k, String.valueOf(v)));
            redis.expire(key, Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("遥测最新值写入失败: deviceKey={}, {}", deviceKey, e.getMessage());
        }
    }

    private Map<String, Object> parseMap(byte[] payload) {
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (Exception e) {
            log.warn("上行报文解析失败: {}", e.getMessage());
        }
        return Map.of();
    }

    /**
     * 指令应答定向回传：reply 可被任意实例消费（共享订阅），
     * 按 requestId 投递到 Redis 信箱，发起实例 BLPOP 取回——跨实例无状态闭环。
     */
    private void deliverCommandReply(byte[] payload) {
        try {
            Map<String, Object> body = objectMapper.readValue(payload, MAP_TYPE);
            Object requestId = body.get("requestId");
            if (requestId == null) {
                log.info("指令应答(无 requestId): {}", textual(payload));
                return;
            }
            com.materin.tech.common.spi.CommandReplySink sink = replySink.getIfAvailable();
            if (sink == null) {
                log.warn("无回执 Sink，丢弃应答 requestId={}", requestId);
                return;
            }
            sink.deliver(String.valueOf(requestId), payload);
        } catch (Exception e) {
            log.warn("指令应答投递失败: {}", e.getMessage());
        }
    }

    private String textual(byte[] payload) {
        return payload == null ? "" : new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    }
}
