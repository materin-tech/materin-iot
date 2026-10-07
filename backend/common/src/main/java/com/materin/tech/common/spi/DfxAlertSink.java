package com.materin.tech.common.spi;

/**
 * DFX 告警落库 SPI：dfx-component 管道产生告警时调用，
 * 由 device-component 实现（device_alert 表归属设备域）。
 * 实现方必须兜底异常，告警失败不影响指标管道。
 */
public interface DfxAlertSink {

    void raise(Long deviceId, String deviceName, String level, String content);
}
