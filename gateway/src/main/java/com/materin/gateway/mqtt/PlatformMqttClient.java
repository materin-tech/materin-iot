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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.gateway.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.materin.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 平台 MQTT 客户端：以 GATEWAY 设备凭证接入平台 EMQX，自动重连 + 订阅下行 + 未连接缓冲补发。
 */
@Slf4j
@Component
public class PlatformMqttClient {

    private static final long RECONNECT_INITIAL_DELAY_SECONDS = 1;
    private static final long RECONNECT_MAX_DELAY_SECONDS = 30;
    private static final long RETRY_DELAY_SECONDS = 3;
    private static final long DISCONNECT_TIMEOUT_SECONDS = 3;
    private static final int PENDING_CAPACITY = 500;

    private final PendingMessageBuffer pendingBuffer = new PendingMessageBuffer(PENDING_CAPACITY);
    private final GatewayProperties properties;
    private Mqtt3AsyncClient client;
    private volatile boolean connected;
    private volatile Consumer<byte[]> downlinkHandler;
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor();

    public PlatformMqttClient(GatewayProperties properties) {
        this.properties = properties;
    }

    public void start(Consumer<byte[]> downlinkHandler) {
        this.downlinkHandler = downlinkHandler;
        GatewayProperties.Platform p = properties.getPlatform();
        if (p.getDeviceKey() == null || p.getDeviceKey().isBlank()) {
            log.error("materin.gateway.platform.device-key 未配置，无法连接平台，网关保持未连接状态");
            return;
        }
        client = MqttClient.builder()
                .useMqttVersion3()
                .identifier(p.getClientId() != null && !p.getClientId().isBlank() ? p.getClientId() : p.getDeviceKey())
                .serverHost(p.getHost())
                .serverPort(p.getPort())
                .automaticReconnect().initialDelay(RECONNECT_INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
                    .maxDelay(RECONNECT_MAX_DELAY_SECONDS, TimeUnit.SECONDS).applyAutomaticReconnect()
                .simpleAuth().username(p.getDeviceKey())
                    .password(p.getSecret() == null ? new byte[0] : p.getSecret().getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                .addConnectedListener(ctx -> {
                    connected = true;
                    log.info("已连接平台 MQTT {}:{}", p.getHost(), p.getPort());
                    subscribeDownlink();
                    flushPending();
                })
                .addDisconnectedListener(ctx -> {
                    connected = false;
                    log.warn("平台 MQTT 连接断开，将自动重连（上行期间入缓冲）");
                })
                .buildAsync();
        attemptConnect();
    }

    private void attemptConnect() {
        client.connectWith().cleanSession(true).send().whenComplete((ack, t) -> {
            if (t != null) {
                log.error("连接平台 MQTT 失败，{} 秒后重试: {}", RETRY_DELAY_SECONDS, t.getMessage());
                retryExecutor.schedule(this::attemptConnect, RETRY_DELAY_SECONDS, TimeUnit.SECONDS); } });
    }

    private void subscribeDownlink() {
        String downlinkTopic = GatewayTopics.downlinkTopic(properties.getPlatform().getDeviceKey());
        client.subscribeWith()
                .topicFilter(downlinkTopic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(this::dispatchDownlink)
                .send().whenComplete((sub, t) -> {
                    if (t != null) { log.error("订阅下行主题 {} 失败: {}", downlinkTopic, t.getMessage()); }
                    else { log.info("已订阅平台下行主题 {}", downlinkTopic); }
                });
    }

    private void dispatchDownlink(Mqtt3Publish publish) {
        try { downlinkHandler.accept(publish.getPayloadAsBytes()); }
        catch (Exception e) { log.error("处理下行消息失败: {}", e.getMessage()); }
    }

    public void publishRaw(String topic, byte[] body) {
        if (client == null || !connected) { // 未连接（启动窗口/断线重连）：入缓冲，连接后按序补发
            pendingBuffer.enqueue(topic, body);
            log.debug("平台 MQTT 未连接，消息已入缓冲（{} 条）：{}", pendingBuffer.size(), topic);
            return;
        }
        try {
            client.publishWith()
                    .topic(topic)
                    .payload(body)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send().whenComplete((pub, t) -> {
                        if (t != null) { log.error("发布到 {} 失败: {}", topic, t.getMessage()); }
                    });
        } catch (Exception e) {
            log.error("发布到 {} 失败: {}", topic, e.getMessage());
        }
    }

    private void flushPending() { pendingBuffer.drain().forEach(m -> publishRaw(m.topic(), m.body())); }

    public void stop() {
        retryExecutor.shutdownNow();
        if (client != null) {
            try { client.disconnect().get(DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS); }
            catch (Exception e) { log.warn("断开平台 MQTT 失败: {}", e.getMessage()); }
        }
    }
}
