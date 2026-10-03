package com.materin.tech.component.device.service;

import com.materin.tech.common.exception.BizException;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.spi.CommandPublisher;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.ProductLookup;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** 设备命令下发：requestId 关联 + Redis 信箱跨实例回传，服务保持无状态。 */
@Service
public class DeviceCommandService {

    private final DeviceMapper deviceMapper;
    private final CommandPublisher commandPublisher;
    private final DeviceCredentialLookup credentialLookup;
    private final ProductLookup productLookup;
    private final StringRedisTemplate redis;
    private final com.materin.tech.common.spi.CommandReplySink replySink;
    private final long cmdTimeoutSeconds;
    /** 全实例等待槽上限：防高并发下 BLPOP 连接/线程耗尽 */
    private final java.util.concurrent.Semaphore waitSlots;

    public DeviceCommandService(DeviceMapper deviceMapper,
                                ObjectProvider<CommandPublisher> commandPublisher,
                                ObjectProvider<DeviceCredentialLookup> credentialLookup,
                                ObjectProvider<ProductLookup> productLookup,
                                StringRedisTemplate redis,
                                ObjectProvider<com.materin.tech.common.spi.CommandReplySink> replySinkRef,
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
        String topic = commandTopic(device);
        String requestId = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("requestId", requestId);
        payload.put("data", command);

        byte[] bytes;
        try {
            bytes = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new BizException(500, "命令序列化失败");
        }
        if (!commandPublisher.publish(topic, bytes)) {
            throw new BizException(503, "MQTT 通道不可用");
        }
        return waitForReply(requestId);
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

    private String commandTopic(Device device) {
        String pk = productLookup == null || device.getProductId() == null ? null
                : productLookup.findById(String.valueOf(device.getProductId()))
                        .map(ProductLookup.ProductSummary::productKey).orElse(null);
        if (pk == null) {
            throw new BizException(400, "设备未关联产品，无法寻址");
        }
        return "materin/" + pk + "/" + device.getDeviceKey() + "/cmd";
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
