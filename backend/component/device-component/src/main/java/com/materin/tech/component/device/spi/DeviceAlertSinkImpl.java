package com.materin.tech.component.device.spi;

import com.materin.tech.common.spi.DfxAlertSink;
import com.materin.tech.component.device.entity.DeviceAlert;
import com.materin.tech.component.device.mapper.DeviceAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** DfxAlertSink 实现：DFX 告警落 device_alert 表（表归属设备域）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAlertSinkImpl implements DfxAlertSink {

    private final DeviceAlertMapper alertMapper;

    @Override
    public void raise(Long deviceId, String deviceName, String level, String content) {
        try {
            DeviceAlert alert = new DeviceAlert();
            alert.setDeviceId(deviceId);
            alert.setDeviceName(deviceName);
            alert.setLevel(level);
            alert.setContent(content);
            alert.setStatus(0); // 0-未处理
            alertMapper.insert(alert);
        } catch (Exception e) {
            log.warn("DFX 告警落库失败（忽略）: deviceId={}, {}", deviceId, e.getMessage());
        }
    }
}
