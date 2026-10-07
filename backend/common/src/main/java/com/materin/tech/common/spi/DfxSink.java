package com.materin.tech.common.spi;

import java.util.Map;

/**
 * DFX 健康指标统一入口 SPI（docs/dfx-monitoring-design.md §1.1）。
 * 各协议适配器（MQTT dfx 分支 / HTTP ingest / SNMP 轮询 / 二期 LwM2M）统一调用，
 * 由 device-component 的管道实现（校验→Redis 最新值→时序库→告警评估）。
 * 实现方必须兜底异常：指标管道故障只降级，绝不阻断接入链路。
 */
public interface DfxSink {

    /**
     * 保存一帧 DFX 指标。
     *
     * @param deviceKey 设备标识（实现方反查设备与产品）
     * @param time      epoch 毫秒
     * @param source    接入协议来源（mqtt / http / snmp / lw m2m），仅用于追踪与展示
     * @param metrics   指标键值（键 = 统一指标字典名，值 = 数值或字符串）
     */
    void saveDfx(String deviceKey, long time, String source, Map<String, Object> metrics);
}
