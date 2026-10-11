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
package com.materin.tech.component.dfx.access.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.mqtt.MqttTopics;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.dfx.core.DfxIngestService;
import com.materin.tech.component.dfx.core.DfxRecord;
import com.materin.tech.component.dfx.core.health.DfxAccessHealth;
import com.materin.tech.component.dfx.core.health.DfxAccessHealthProvider;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * DFX MQTT 接入实现（dfx-access-mqtt）：独立轻量客户端共享订阅
 * $share/{group}/materin/+/+/dfx，与主遥测链路（network-mqtt）分组隔离——
 * 关闭 DFX MQTT 接入不影响设备数据上报主链路。
 * dfx 上行同样刷新在线 TTL（在线判定仍以 MQTT 连接层为准）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "materin.dfx.access.mqtt", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DfxMqttAccess implements DfxAccessHealthProvider {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DfxMqttAccessProperties properties;
    private final ObjectProvider<DfxIngestService> ingest;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private Mqtt3AsyncClient client;
    private volatile boolean connected = false;

    public DfxMqttAccess(DfxMqttAccessProperties properties,
                         ObjectProvider<DfxIngestService> ingest,
                         ObjectProvider<DeviceCredentialLookup> credentialLookup,
                         StringRedisTemplate redis,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.ingest = ingest;
        this.credentialLookup = credentialLookup;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @jakarta.annotation.PostConstruct
    public void start() {
        if (ingest.getIfAvailable() == null) {
            log.info("DFX MQTT 接入未启动：核心管道不可用");
            return;
        }
        try {
            client = MqttClient.builder()
                    .useMqttVersion3()
                    .identifier("materin-dfx-" + java.util.UUID.randomUUID()
                            .toString().substring(0, 8))
                    .serverHost(properties.getHost())
                    .serverPort(properties.getPort())
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();
            client.connectWith()
                    .simpleAuth()
                    .username(properties.getUsername())
                    .password(properties.getPassword().getBytes())
                    .applySimpleAuth()
                    .send()
                    .whenComplete((connAck, throwable) -> {
                        if (throwable != null) {
                            log.warn("DFX MQTT 连接失败（自动重连）: {}", throwable.getMessage());
                            return;
                        }
                        connected = true;
                        subscribeDfx();
                    });
        } catch (Exception e) {
            log.warn("DFX MQTT 启动失败: {}", e.getMessage());
        }
    }

    private void subscribeDfx() {
        String shared = "$share/" + properties.getShareGroup() + "/materin/+/+/dfx";
        client.subscribeWith()
                .topicFilter(shared)
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(publish -> {
                    String topic = publish.getTopic().toString();
                    String[] parsed = MqttTopics.parse(topic);
                    if (parsed == null) {
                        return;
                    }
                    String deviceKey = parsed[1];
                    touchOnline(deviceKey);
                    com.materin.tech.common.core.TraceIds.setMdc(null);
                    try {
                        Map<String, Object> metrics =
                                objectMapper.readValue(publish.getPayloadAsBytes(), MAP_TYPE);
                        // 报文携带 traceId 时以其为准，并从指标中剔除（metrics 直落时序库）
                        Object traceId = metrics.remove(
                                com.materin.tech.common.core.TraceIds.PAYLOAD_KEY);
                        if (traceId != null) {
                            org.slf4j.MDC.put(com.materin.tech.common.core.TraceIds.MDC_KEY,
                                    com.materin.tech.common.core.TraceIds.normalize(
                                            String.valueOf(traceId)));
                        }
                        ingest.getIfAvailable().ingest(
                                new DfxRecord(deviceKey, System.currentTimeMillis(), "mqtt", metrics));
                    } catch (Exception e) {
                        log.warn("DFX MQTT 报文解析失败: {}", e.getMessage());
                    } finally {
                        com.materin.tech.common.core.TraceIds.clearMdc();
                    }
                })
                .send()
                .whenComplete((ack, throwable) -> {
                    if (throwable != null) {
                        log.warn("DFX 共享订阅失败: {}", throwable.getMessage());
                    } else {
                        log.info("DFX 共享订阅成功: {}", shared);
                    }
                });
    }

    private void touchOnline(String deviceKey) {
        try {
            redis.opsForValue().set(RedisKeys.DEVICE_ONLINE + deviceKey, "1",
                    Duration.ofSeconds(properties.getOnlineTtlSeconds()));
            credentialLookup.getIfAvailable().findByKey(deviceKey).ifPresent(cred ->
                    credentialLookup.getIfAvailable().touchOnline(
                            cred.deviceId(), java.time.LocalDateTime.now()));
        } catch (Exception ignore) {
            // 在线刷新失败不阻断指标管道
        }
    }

    @Override
    public DfxAccessHealth health() {
        return new DfxAccessHealth("mqtt", properties.isEnabled(),
                !properties.isEnabled() ? "DISABLED" : connected ? "UP" : "DOWN",
                connected ? "共享订阅 $share/" + properties.getShareGroup()
                        + "/materin/+/+/dfx 已建立" : "未连接（等待重连）",
                null);
    }
}