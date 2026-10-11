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
package com.materin.tech.component.device.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.common.mqtt.MqttTopics;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.spi.CommandPublisher;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.ProductLookup;
import com.materin.tech.common.spi.TelemetrySink;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** 设备命令下发：requestId 关联 + Redis 信箱跨实例回传，服务保持无状态。 */
@Slf4j
@Service
public class DeviceCommandService {

    private final DeviceMapper deviceMapper;
    private final CommandPublisher commandPublisher;
    private final DeviceCredentialLookup credentialLookup;
    private final ProductLookup productLookup;
    private final StringRedisTemplate redis;
    private final com.materin.tech.common.spi.CommandReplySink replySink;
    private final ObjectProvider<TelemetrySink> telemetrySink;
    private final ObjectMapper objectMapper;
    private final long cmdTimeoutSeconds;
    /** 全实例等待槽上限：防高并发下 BLPOP 连接/线程耗尽 */
    private final java.util.concurrent.Semaphore waitSlots;

    public DeviceCommandService(DeviceMapper deviceMapper,
                                ObjectProvider<CommandPublisher> commandPublisher,
                                ObjectProvider<DeviceCredentialLookup> credentialLookup,
                                ObjectProvider<ProductLookup> productLookup,
                                StringRedisTemplate redis,
                                ObjectProvider<com.materin.tech.common.spi.CommandReplySink> replySinkRef,
                                ObjectProvider<TelemetrySink> telemetrySink,
                                ObjectMapper objectMapper,
                                @org.springframework.beans.factory.annotation.Value(
                                        "${materin.command.timeout-seconds:10}") long cmdTimeoutSeconds,
                                @org.springframework.beans.factory.annotation.Value(
                                        "${materin.command.max-concurrent-waits:100}") int waitSlotsMax) {
        this.deviceMapper = deviceMapper;
        this.commandPublisher = commandPublisher.getIfAvailable();
        this.credentialLookup = credentialLookup.getIfAvailable();
        this.productLookup = productLookup.getIfAvailable();
        this.redis = redis;
        this.replySink = replySinkRef.getIfAvailable();
        this.telemetrySink = telemetrySink;
        this.objectMapper = objectMapper;
        this.cmdTimeoutSeconds = cmdTimeoutSeconds;
        this.waitSlots = new java.util.concurrent.Semaphore(waitSlotsMax);
    }

    /**
     * 下发命令并同步等待设备应答（跨实例回传）。
     *
     * @return 设备应答 JSON 字符串
     */
    public String send(Long deviceId, Map<String, Object> command) {
        requirePublisher();
        Device device = requireOnlineDevice(deviceId);
        String pk = resolveProductKey(device);
        String requestId = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("requestId", requestId);
        // 注入链路追踪：HTTP 线程 MDC 已由 TraceFilter 设置（前端注入或后端生成），非 HTTP 调用方兜底生成
        payload.put(com.materin.tech.common.core.TraceIds.MDC_KEY,
                com.materin.tech.common.core.TraceIds.current());
        payload.put("data", command);
        String topic;
        if (isChildDevice(device)) {
            // 子设备命令经其归属网关的专属下行主题转发（多网关隔离），附带完整回执主题供网关代子设备发布应答
            Device gateway = device.getGatewayId() == null ? null
                    : deviceMapper.selectOneById(device.getGatewayId());
            if (gateway == null || !"GATEWAY".equals(gateway.getDeviceType())) {
                throw new BizException(400, "子设备未绑定有效网关，无法下发命令");
            }
            topic = MqttTopics.gatewayDownlinkTopic(gateway.getDeviceKey());
            payload.put("device", device.getDeviceKey());
            payload.put("replyTopic", "materin/" + pk + "/" + device.getDeviceKey() + "/reply");
        } else {
            topic = "materin/" + pk + "/" + device.getDeviceKey() + "/cmd";
        }

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new BizException(500, "命令序列化失败");
        }
        if (!commandPublisher.publish(topic, bytes)) {
            throw new BizException(503, "MQTT 通道不可用");
        }
        long begin = System.currentTimeMillis();
        String response = null;
        boolean success = true;
        try {
            response = waitForReply(requestId);
            return response;
        } catch (RuntimeException e) {
            success = false;
            response = e.getMessage();
            throw e;
        } finally {
            recordMethodLog(device, command, requestId, response, success,
                    System.currentTimeMillis() - begin);
        }
    }

    /** 方法调用记录落时序库（闭环后异步语义，失败不影响主流程）。 */
    private void recordMethodLog(Device device, Map<String, Object> command, String requestId,
                                 String response, boolean success, long costMs) {
        TelemetrySink sink = telemetrySink.getIfAvailable();
        if (sink == null) {
            return;
        }
        try {
            Map<String, Object> request = new java.util.LinkedHashMap<>(command);
            request.put("requestId", requestId);
            sink.saveMethodRecord(device.getId(), firstText(command, "name", "method", "identifier", "id"),
                    objectMapper.writeValueAsString(request), response, success, costMs);
        } catch (Exception e) {
            log.warn("方法调用记录落库失败: deviceId={}, {}", device.getId(), e.getMessage());
        }
    }

    private String firstText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "unknown";
    }

    private String waitForReply(String requestId) {
        if (replySink == null) {
            throw new BizException(503, "回执组件未启用");
        }
        if (!waitSlots.tryAcquire()) {
            throw new BizException(429, "命令等待并发已达上限");
        }
        try {
            String key = RedisKeys.CMD_REPLY + requestId;
            String delivered = redis.opsForList().leftPop(key, Duration.ofSeconds(cmdTimeoutSeconds));
            if (delivered == null) {
                throw new BizException(504, "设备应答超时");
            }
            return replySink.resolve(delivered);
        } finally {
            waitSlots.release();
        }
    }

    /** 解析设备所属产品 productKey（下行寻址/replyTopic 构造共用）。 */
    private String resolveProductKey(Device device) {
        String pk = productLookup == null || device.getProductId() == null ? null
                : productLookup.findById(String.valueOf(device.getProductId()))
                        .map(ProductLookup.ProductSummary::productKey).orElse(null);
        if (pk == null) {
            throw new BizException(400, "设备未关联产品，无法寻址");
        }
        return pk;
    }

    private boolean isChildDevice(Device device) {
        return "CHILD".equals(device.getDeviceType());
    }

    private Device requireOnlineDevice(Long deviceId) {
        Device device = deviceMapper.selectOneByQuery(QueryWrapper.create().eq("id", deviceId));
        if (device == null) {
            throw BizException.notFound("设备不存在: " + deviceId);
        }
        String onlineKey = RedisKeys.DEVICE_ONLINE + device.getDeviceKey();
        if (!Boolean.TRUE.equals(redis.hasKey(onlineKey))) {
            throw new BizException(409, "设备离线，拒绝下发");
        }
        return device;
    }

    private void requirePublisher() {
        if (commandPublisher == null) {
            throw new BizException(503, "MQTT 组件未启用");
        }
    }
}
