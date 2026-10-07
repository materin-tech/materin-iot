package com.materin.tech.component.dfx.core;

import java.util.Map;

/**
 * DFX 统一接入数据结构（KV 信息模型，协议无关）：
 * 各协议适配器（MQTT/HTTP/SNMP/未来的 LwM2M）把报文解析为此结构后交管道。
 * metrics 键 = 统一指标字典名（docs/dfx-monitoring-design.md §3），值 = 数值/字符串。
 */
public record DfxRecord(String deviceKey, long time, String source, Map<String, Object> metrics) {
}
