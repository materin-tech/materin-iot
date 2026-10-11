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
package com.materin.tech.component.ota.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.mqtt.MqttTopics;
import com.materin.tech.component.ota.service.OtaTaskService;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OTA 上行通道（materin/+/+/ota 进度上报）：独立轻量客户端共享订阅，
 * 与主遥测/DFX 链路分组隔离。关闭 OTA 组件不影响其它链路。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "materin.ota", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class OtaMqttSubscriber {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectProvider<OtaTaskService> taskService;
    private final ObjectMapper objectMapper;

    private Mqtt3AsyncClient client;

    public OtaMqttSubscriber(ObjectProvider<OtaTaskService> taskService,
                             ObjectMapper objectMapper,
                             @Value("${materin.network.mqtt.host:127.0.0.1}") String host,
                             @Value("${materin.network.mqtt.port:1883}") int port,
                             @Value("${materin.network.mqtt.service-username:materin-svc-consumer}") String username,
                             @Value("${materin.network.mqtt.service-password:materin-svc-dev-password}") String password) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
        try {
            client = MqttClient.builder()
                    .useMqttVersion3()
                    .identifier("materin-ota-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                    .serverHost(host)
                    .serverPort(port)
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();
            client.connectWith()
                    .simpleAuth()
                    .username(username)
                    .password(password.getBytes())
                    .applySimpleAuth()
                    .send()
                    .whenComplete((ack, throwable) -> {
                        if (throwable != null) {
                            log.warn("OTA MQTT 连接失败（自动重连）: {}", throwable.getMessage());
                            return;
                        }
                        subscribe();
                    });
        } catch (Exception e) {
            log.warn("OTA MQTT 启动失败: {}", e.getMessage());
        }
    }

    private void subscribe() {
        String shared = "$share/materin-ota/materin/+/+/ota";
        client.subscribeWith()
                .topicFilter(shared)
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(publish -> {
                    String[] parsed = MqttTopics.parse(publish.getTopic().toString());
                    if (parsed == null) {
                        return;
                    }
                    com.materin.tech.common.core.TraceIds.setMdc(null);
                    try {
                        Map<String, Object> body =
                                objectMapper.readValue(publish.getPayloadAsBytes(), MAP_TYPE);
                        Object traceId = body.remove(
                                com.materin.tech.common.core.TraceIds.PAYLOAD_KEY);
                        if (traceId != null) {
                            org.slf4j.MDC.put(com.materin.tech.common.core.TraceIds.MDC_KEY,
                                    com.materin.tech.common.core.TraceIds.normalize(
                                            String.valueOf(traceId)));
                        }
                        taskService.getIfAvailable()
                                .onProgress(parsed[1], body);
                    } catch (Exception e) {
                        log.warn("OTA 进度处理失败: {}", e.getMessage());
                    } finally {
                        com.materin.tech.common.core.TraceIds.clearMdc();
                    }
                })
                .send()
                .whenComplete((ack, throwable) -> {
                    if (throwable != null) {
                        log.warn("OTA 共享订阅失败: {}", throwable.getMessage());
                    } else {
                        log.info("OTA 共享订阅成功: {}", shared);
                    }
                });
    }
}
