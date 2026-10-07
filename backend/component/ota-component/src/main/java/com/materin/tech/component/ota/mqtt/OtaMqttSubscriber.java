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
                    try {
                        Map<String, Object> body =
                                objectMapper.readValue(publish.getPayloadAsBytes(), MAP_TYPE);
                        taskService.getIfAvailable()
                                .onProgress(parsed[1], body);
                    } catch (Exception e) {
                        log.warn("OTA 进度处理失败: {}", e.getMessage());
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
