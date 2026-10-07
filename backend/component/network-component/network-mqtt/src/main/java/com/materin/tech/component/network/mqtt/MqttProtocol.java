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
    private Mqtt3AsyncClient client;

    public MqttProtocol(MqttProperties properties, TelemetryHandler telemetryHandler) {
        this.properties = properties;
        this.telemetryHandler = telemetryHandler;
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
        subscribeShared(MqttTopics.WILDCARD_DFX);
    }

    private void subscribeShared(String topic) {
        String shared = "$share/" + MqttTopics.SHARE_GROUP + "/" + topic;
        client.subscribeWith()
                .topicFilter(shared)
                .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                .callback(publish -> {
                    String topicName = publish.getTopic().toString();
                    String[] parsed = MqttTopics.parse(topicName);
                    if (parsed != null) {
                        telemetryHandler.handle(parsed[0], parsed[1], parsed[2],
                                publish.getPayloadAsBytes());
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
