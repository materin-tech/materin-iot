package com.materin.tech.common.spi;

import java.util.List;

/**
 * DFX SNMP 轮询目标查询 SPI：network-snmp 轮询器经此获取待轮询目标，
 * 由 device-component 实现（dfx_snmp_target 表）。
 * 接口隔离：轮询器不感知表结构与存储细节。
 */
public interface DfxSnmpTargetLookup {

    /** 全部启用的轮询目标（轮询器自行按 interval 节流）。 */
    List<SnmpTarget> findEnabled();

    /** SNMP 轮询目标（v2c）。 */
    record SnmpTarget(Long deviceId, String deviceKey, String host, int port,
                      String community, int intervalSeconds) {
    }
}
