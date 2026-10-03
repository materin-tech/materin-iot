package com.materin.tech.component.device.spi;

import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 设备在线状态落库：按设备节流（60s）更新 last_online，避免高频上报打爆 DB。 */
@Component
@RequiredArgsConstructor
public class DeviceOnlineWriter {

    private static final long THROTTLE_MS = 60_000;

    private final DeviceMapper deviceMapper;
    private final Map<Long, Long> lastWrites = new ConcurrentHashMap<>();

    public void touch(Long deviceId) {
        long now = System.currentTimeMillis();
        Long last = lastWrites.get(deviceId);
        if (last != null && now - last < THROTTLE_MS) {
            return;
        }
        lastWrites.put(deviceId, now);
        Device patch = new Device();
        patch.setId(deviceId);
        patch.setLastOnline(LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        deviceMapper.update(patch);
    }
}
