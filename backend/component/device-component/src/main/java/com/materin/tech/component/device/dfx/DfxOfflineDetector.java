package com.materin.tech.component.device.dfx;

import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.entity.DeviceAlert;
import com.materin.tech.component.device.mapper.DeviceAlertMapper;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备在线状态巡检（补齐 status 字段无人维护的历史缺口）：
 * 有 Redis 在线标记而 status!=1 → 置 1；无标记而 status!=2 → 置 2 并产生一次离线告警。
 * 判定权威是 MQTT 连接层消息（SNMP 轮询结果不改变在线状态，见设计文档 §2）。
 */
@Slf4j
@Component
public class DfxOfflineDetector {

    private final DeviceMapper deviceMapper;
    private final DeviceAlertMapper alertMapper;

    public DfxOfflineDetector(DeviceMapper deviceMapper, DeviceAlertMapper alertMapper,
                              ObjectProvider<StringRedisTemplate> redisProvider) {
        this.deviceMapper = deviceMapper;
        this.alertMapper = alertMapper;
        this.redis = redisProvider.getIfAvailable();
    }

    private final StringRedisTemplate redis;

    /** 60s 巡检一次在线状态迁移。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void patrol() {
        if (redis == null) {
            return;
        }
        List<Device> devices = deviceMapper.selectAll();
        boolean online;
        for (Device d : devices) {
            online = redis.hasKey(RedisKeys.DEVICE_ONLINE + d.getDeviceKey());
            if (online && d.getStatus() == null || online && d.getStatus() != 1) {
                d.setStatus(1);
                deviceMapper.update(d);
            } else if (!online && d.getStatus() == null || !online && d.getStatus() != 2) {
                d.setStatus(2);
                deviceMapper.update(d);
                raiseOfflineAlert(d);
            }
        }
    }

    private void raiseOfflineAlert(Device d) {
        DeviceAlert a = new DeviceAlert();
        a.setDeviceId(d.getId());
        a.setDeviceName(d.getName());
        a.setLevel("warn");
        a.setContent("设备离线（超过在线判定窗口无任何上行消息）");
        a.setStatus(0); // 0-未处理
        alertMapper.insert(a);
        log.info("设备离线告警: {}", d.getDeviceKey());
    }
}
