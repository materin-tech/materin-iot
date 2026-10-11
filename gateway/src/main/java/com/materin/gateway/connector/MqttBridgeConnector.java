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
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.materin.gateway.core.Connector;
import com.materin.gateway.core.ConnectorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * MQTT 桥接 connector（type: mqtt-bridge）：连接子 broker，上行转发 + 命令下发到 command-topic。
 */
@Slf4j
public class MqttBridgeConnector implements Connector {

    private static final long DEFAULT_TIMEOUT_SECONDS = 5;

    private final String id;
    private final String brokerUrl;
    private final String clientId;
    private final String username;
    private final String password;
    private final List<BridgeTopicMapping> mappings;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile ConnectorContext ctx;
    private volatile MqttBridgeForwarder forwarder;
    private Mqtt3AsyncClient client;

    public MqttBridgeConnector(Map<String, Object> cfg) {
        this.id = Configs.stringOf(cfg, "id", "mqtt-bridge");
        this.brokerUrl = Configs.stringOf(cfg, "broker-url", null);
        this.clientId = Configs.stringOf(cfg, "client-id", "materin-bridge-" + id);
        this.username = Configs.stringOf(cfg, "username", null);
        this.password = Configs.stringOf(cfg, "password", null);
        this.mappings = BridgeTopicMapping.parseList(cfg.get("mappings"));
        if (brokerUrl == null || brokerUrl.isBlank()) {
            throw new IllegalArgumentException("mqtt-bridge connector 需要配置 broker-url");
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return "mqtt-bridge";
    }

    @Override
    public void start(ConnectorContext ctx) {
        this.ctx = ctx;
        this.forwarder = new MqttBridgeForwarder(id, ctx);
        String hostPort = brokerUrl.replaceFirst("^tcp://", "");
        int port = hostPort.contains(":") ? Integer.parseInt(hostPort.substring(hostPort.lastIndexOf(':') + 1)) : 1883;
        String host = hostPort.contains(":") ? hostPort.substring(0, hostPort.indexOf(':')) : hostPort;
        Mqtt3ClientBuilder builder = MqttClient.builder()
                .useMqttVersion3()
                .identifier(clientId)
                .serverHost(host)
                .serverPort(port);
        if (username != null) {
            builder.simpleAuth().username(username)
                    .password(password == null ? new byte[0] : password.getBytes(StandardCharsets.UTF_8)).applySimpleAuth();
        }
        client = builder.buildAsync();
        client.connectWith().cleanSession(true).send().whenComplete((ack, t) -> {
            if (t != null) {
                log.warn("[{}] 连接子 broker {} 失败: {}", id, brokerUrl, t.getMessage());
                return;
            }
            mappings.forEach(this::subscribe);
        });
    }

    private void subscribe(BridgeTopicMapping mapping) {
        client.subscribeWith()
                .topicFilter(mapping.topicFilter())
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(publish -> forwarder.forward(mapping, publish))
                .send().whenComplete((sub, t) -> {
                    if (t != null) { log.warn("[{}] 订阅 {} 失败: {}", id, mapping.topicFilter(), t.getMessage()); }
                });
    }

    @Override
    public void onCommand(String deviceKey, Map<String, Object> data, String requestId) {
        for (BridgeTopicMapping mapping : mappings) {
            String topic = mapping.renderCommandTopic(deviceKey);
            if (topic != null) {
                publishCommand(topic, data, deviceKey, requestId);
                return;
            }
        }
        log.debug("[{}] 无 command-topic 映射，忽略 deviceKey={} 的命令", id, deviceKey);
    }

    private void publishCommand(String topic, Map<String, Object> data, String deviceKey, String requestId) {
        try {
            client.publishWith().topic(topic).payload(mapper.writeValueAsBytes(data))
                    .qos(MqttQos.AT_LEAST_ONCE).send();
            ctx.reply(deviceKey, requestId, Map.of("status", "ok"));
        } catch (Exception e) {
            log.warn("[{}] 命令 publish note 下发到子 broker 失败: {}", id, e.getMessage());
            ctx.reply(deviceKey, requestId, Map.of("error", "publish to child broker failed"));
        }
    }

    @Override
    public void stop() {
        if (client != null) {
            try {
                client.disconnect().get(DEFAULT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[{}] 断开子 broker 失败: {}", id, e.getMessage());
            }
        }
    }
}
