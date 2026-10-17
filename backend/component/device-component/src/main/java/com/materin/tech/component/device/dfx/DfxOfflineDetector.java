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
package com.materin.tech.component.device.dfx;

import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.entity.DeviceAlert;
import com.materin.tech.component.device.mapper.DeviceAlertMapper;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备在线状态巡检（补齐 status 字段无人维护的历史缺口）：
 * 有 Redis 在线标记而 status!=1 → 置 1 并恢复在案的离线告警；
 * 无标记而 status!=2 → 置 2 并产生离线告警（同设备活跃告警合并累加，不重复插行）。
 * 判定权威是 MQTT 连接层消息（SNMP 轮询结果不改变在线状态，见设计文档 §2）。
 */
@Slf4j
@Component
public class DfxOfflineDetector {

    /** 巡检游标分批大小（内存恒定，不随设备规模增长） */
    static final int SCAN_BATCH = 500;

    private final DeviceMapper deviceMapper;
    private final DeviceAlertMapper alertMapper;
    private final StringRedisTemplate redis;

    public DfxOfflineDetector(DeviceMapper deviceMapper, DeviceAlertMapper alertMapper,
                              StringRedisTemplate redis) {
        this.deviceMapper = deviceMapper;
        this.alertMapper = alertMapper;
        this.redis = redis;
    }

    /** 60s 巡检一次在线状态迁移（游标分批，全量覆盖）。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void patrol() {
        long cursor = 0L;
        while (true) {
            List<Device> batch = deviceMapper.selectListByQuery(
                    QueryWrapper.create().gt("id", cursor).orderBy("id", true)
                            .limit(SCAN_BATCH));
            if (batch.isEmpty()) {
                return;
            }
            for (Device d : batch) {
                cursor = d.getId();
                patrolOne(d);
            }
            if (batch.size() < SCAN_BATCH) {
                return;
            }
        }
    }

    private void patrolOne(Device d) {
        boolean online = Boolean.TRUE.equals(
                redis.hasKey(RedisKeys.DEVICE_ONLINE + d.getDeviceKey()));
        Integer status = d.getStatus();
        if (online && !Integer.valueOf(1).equals(status)) {
            d.setStatus(1);
            deviceMapper.update(d);
            clearOfflineAlert(d);
        } else if (!online && !Integer.valueOf(2).equals(status)) {
            d.setStatus(2);
            deviceMapper.update(d);
            raiseOfflineAlert(d);
        }
    }

    /** 离线告警（metric=offline）合并：已有活跃告警累加次数，否则新建。 */
    private void raiseOfflineAlert(Device d) {
        DeviceAlert active = alertMapper.selectOneByQuery(QueryWrapper.create()
                .eq("device_id", d.getId()).eq("metric", "offline").eq("status", 0));
        if (active != null) {
            active.setTriggerCount(active.getTriggerCount() == null ? 2 : active.getTriggerCount() + 1);
            active.setLastTime(java.time.LocalDateTime.now());
            alertMapper.update(active);
            return;
        }
        DeviceAlert a = new DeviceAlert();
        a.setDeviceId(d.getId());
        a.setDeviceName(d.getName());
        a.setLevel("warn");
        a.setContent("设备离线（超过在线判定窗口无任何上行消息）");
        a.setMetric("offline");
        a.setTriggerCount(1);
        a.setLastTime(java.time.LocalDateTime.now());
        a.setStatus(0);
        alertMapper.insert(a);
        log.info("设备离线告警: {}", d.getDeviceKey());
    }

    /** 设备上线：恢复在案的离线告警（置已恢复）。 */
    private void clearOfflineAlert(Device d) {
        DeviceAlert active = alertMapper.selectOneByQuery(QueryWrapper.create()
                .eq("device_id", d.getId()).eq("metric", "offline").eq("status", 0));
        if (active != null) {
            active.setStatus(2);
            active.setClearTime(java.time.LocalDateTime.now());
            active.setClearReason("设备上线");
            alertMapper.update(active);
        }
    }
}
