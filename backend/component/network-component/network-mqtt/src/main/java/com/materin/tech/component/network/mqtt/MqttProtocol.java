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

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.materin.tech.common.mqtt.MqttTopics;
import com.materin.tech.common.spi.NetworkProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * MQTT 协议接入：连接 EMQX，以共享订阅消费全部上行消息
 * （$share/{group}/materin/+/+/report 等多实例负载均衡）。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "materin.network.mqtt", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MqttProperties.class)
public class MqttProtocol implements NetworkProtocol, com.materin.tech.common.spi.CommandPublisher {

    private final MqttProperties properties;
    private final TelemetryHandler telemetryHandler;
    private final GatewayHandler gatewayHandler;
    private Mqtt3AsyncClient client;

    public MqttProtocol(MqttProperties properties, TelemetryHandler telemetryHandler,
                        GatewayHandler gatewayHandler) {
        this.properties = properties;
        this.telemetryHandler = telemetryHandler;
        this.gatewayHandler = gatewayHandler;
    }

    @Override
    public String name() {
        return "mqtt";
    }

    @Override
    public void start() {
        client = MqttClient.builder()
                .useMqttVersion3()
                .identifier("materin-svc-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                .serverHost(properties.getHost())
                .serverPort(properties.getPort())
                .automaticReconnectWithDefaultConfig()
                .buildAsync();

        client.connectWith()
                .simpleAuth()
                .username(properties.getServiceUsername())
                .password(properties.getServicePassword().getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth()
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        log.warn("MQTT 连接失败（将自动重连）: {}", throwable.getMessage());
                        return;
                    }
                    log.info("MQTT 已连接 EMQX {}:{}", properties.getHost(), properties.getPort());
                    subscribeUplinks();
                });
    }

    /** 共享订阅：多实例消费同一份上行流量，EMQX 侧自动分组负载均衡。 */
    private void subscribeUplinks() {
        subscribeShared(MqttTopics.WILDCARD_REPORT);
        subscribeShared(MqttTopics.WILDCARD_EVENT);
        subscribeShared(MqttTopics.WILDCARD_REPLY);
        subscribeShared(MqttTopics.WILDCARD_GATEWAY);
    }

    private void subscribeShared(String topic) {
        String shared = "$share/" + MqttTopics.SHARE_GROUP + "/" + topic;
        client.subscribeWith()
                .topicFilter(shared)
                .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                .callback(publish -> {
                    String topicName = publish.getTopic().toString();
                    // 共享订阅报文无独立 trace 头：回调线程入口生成，报文自带 traceId 时由处理器覆盖
                    com.materin.tech.common.core.TraceIds.setMdc(null);
                    try {
                        if (MqttTopics.isGatewayTopic(topicName)) {
                            gatewayHandler.handle(topicName, publish.getPayloadAsBytes());
                            return;
                        }
                        String[] parsed = MqttTopics.parse(topicName);
                        if (parsed != null) {
                            telemetryHandler.handle(parsed[0], parsed[1], parsed[2],
                                    publish.getPayloadAsBytes());
                        }
                    } finally {
                        com.materin.tech.common.core.TraceIds.clearMdc();
                    }
                })
                .send()
                .whenComplete((ack, throwable) -> {
                    if (throwable != null) {
                        log.warn("共享订阅失败 {} : {}", topic, throwable.getMessage());
                    } else {
                        log.info("共享订阅成功: {}", shared);
                    }
                });
    }

    /** 下行发布：客户端未连接时返回 false，由调用方决定失败语义。 */
    @Override
    public boolean publish(String topic, byte[] payload) {
        if (client == null) {
            return false;
        }
        try {
            client.publishWith()
                    .topic(topic)
                    .payload(payload)
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    .send()
                    .get(3, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.warn("下行发布失败: topic={} err={}", topic, e.getMessage());
            return false;
        }
    }

    @Override
    public void stop() {
        if (client != null) {
            client.disconnect().orTimeout(3, TimeUnit.SECONDS);
        }
    }
}
