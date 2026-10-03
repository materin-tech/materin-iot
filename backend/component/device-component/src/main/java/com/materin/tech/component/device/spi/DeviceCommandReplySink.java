package com.materin.tech.component.device.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.spi.CommandReplySink;
import com.materin.tech.component.device.entity.DeviceCommandLog;
import com.materin.tech.component.device.mapper.DeviceCommandLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 回执投递实现：小报文直进 Redis 信箱；大报文落库+引用，防大 key。 */
@Component
public class DeviceCommandReplySink implements CommandReplySink {

    private final DeviceCommandLogMapper logMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int bigPayloadThreshold;

    public DeviceCommandReplySink(DeviceCommandLogMapper logMapper,
                                  StringRedisTemplate redis,
                                  ObjectMapper objectMapper,
                                  @Value("${materin.command.big-payload-threshold:16384}") int bigPayloadThreshold) {
        this.logMapper = logMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.bigPayloadThreshold = bigPayloadThreshold;
    }

    @Override
    public void deliver(String requestId, byte[] payload) {
        String text = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
        String delivered;
        if (text.length() > bigPayloadThreshold) {
            // 大报文：落库，Redis 只放引用（防大 key）
            DeviceCommandLog log = new DeviceCommandLog();
            log.setRequestId(requestId);
            log.setDirection(2);
            log.setPayload(text);
            logMapper.insert(log);
            delivered = objectMapper.valueToTree(java.util.Map.of(
                    "ref", "db", "logId", String.valueOf(log.getId()))).toString();
        } else {
            delivered = text;
        }
        String key = RedisKeys.CMD_REPLY + requestId;
        redis.opsForList().rightPush(key, delivered);
        redis.expire(key, Duration.ofSeconds(60));
    }

    @Override
    public String resolve(String deliveredPayload) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(deliveredPayload);
            if (node.has("ref") && "db".equals(node.get("ref").asText())) {
                DeviceCommandLog log = logMapper.selectOneByQuery(
                        QueryWrapper.create().eq("id", node.get("logId").asLong()));
                return log == null ? deliveredPayload : log.getPayload();
            }
            return deliveredPayload;
        } catch (Exception e) {
            return deliveredPayload;
        }
    }
}
