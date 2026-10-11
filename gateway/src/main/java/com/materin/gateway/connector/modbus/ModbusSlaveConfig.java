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
package com.materin.gateway.connector.modbus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.materin.gateway.connector.Configs;

/**
 * modbus-tcp connector 配置模型与解析：slaves 列表 → SlaveConfig（含寄存器定义）。
 * 解析失败抛 IllegalArgumentException，由 ConnectorRegistry 捕获降级。
 */
public record ModbusSlaveConfig(String host, int port, int unitId, long pollPeriodMs, String deviceKey,
        List<RegisterDef> registers) {

    private static final int DEFAULT_PORT = 502;
    private static final long DEFAULT_POLL_MS = 1000L;
    private static final int DEFAULT_UNIT_ID = 1;

    /** 解析 connectors 条目中的 slaves 字段 */
    public static List<ModbusSlaveConfig> parseSlaves(Object raw) {
        List<ModbusSlaveConfig> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> map = Configs.castMap(item);
                String deviceKey = Configs.stringOf(map, "device-key", null);
                if (deviceKey == null || deviceKey.isBlank()) {
                    throw new IllegalArgumentException("modbus-tcp 的每个 slave 必须配置 device-key");
                }
                result.add(new ModbusSlaveConfig(
                        Configs.stringOf(map, "host", "127.0.0.1"),
                        (int) Configs.longOf(map, "port", DEFAULT_PORT),
                        (int) Configs.longOf(map, "unit-id", DEFAULT_UNIT_ID),
                        Configs.longOf(map, "poll-period-ms", DEFAULT_POLL_MS),
                        deviceKey,
                        parseRegisters(map.get("registers"))));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("modbus-tcp connector 需要至少一个 slaves 配置");
        }
        return List.copyOf(result);
    }

    private static List<RegisterDef> parseRegisters(Object raw) {
        List<RegisterDef> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> map = Configs.castMap(item);
                String name = Configs.stringOf(map, "name", null);
                if (name == null || name.isBlank()) {
                    continue;
                }
                String functionCode = Configs.stringOf(map, "function-code", "readHoldingRegisters").trim();
                if (!SUPPORTED_FUNCTIONS.contains(functionCode.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException(
                            "不支持的 function-code: " + functionCode + "，支持: " + SUPPORTED_FUNCTIONS);
                }
                result.add(new RegisterDef(name,
                        (int) Configs.longOf(map, "address", 0),
                        functionCode,
                        Configs.stringOf(map, "data-type", "int16").trim(),
                        Configs.doubleOf(map, "scale", 1.0)));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("每个 slave 需要至少一条 registers 配置");
        }
        return List.copyOf(result);
    }

    private static final List<String> SUPPORTED_FUNCTIONS = List.of(
            "readHoldingRegisters", "readInputRegisters", "readCoils");

    /** 寄存器定义：name → 遥测键 / 写命令定位名 */
    public record RegisterDef(String name, int address, String functionCode, String dataType, double scale) {

        public boolean isWide() {
            return ModbusValueCodec.isWide(dataType);
        }
    }

    /** 调试输出（寄存器映射概览） */
    public String summary() {
        return registers.stream()
                .map(r -> String.format("%s@%d[%s/%s]", r.name(), r.address(), r.functionCode(), r.dataType()))
                .collect(Collectors.joining(", "));
    }
}
