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
 * furnished to do so, subject to final conditions:
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

import java.util.List;
import java.util.Map;

import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.materin.gateway.connector.Configs;
import com.materin.gateway.core.ConnectorContext;

/**
 * Modbus 写命令执行器：writeRegister / writeCoil 按 registers 声明的 name 定位地址，
 * 执行结果经 ConnectorContext 回执（成功 status=ok，失败 error 信息）。
 */
public class ModbusCommandWriter {

    private final List<ModbusSlaveConfig> slaves;
    private final Map<String, ModbusTcpClient> clients;
    private final ConnectorContext ctx;

    public ModbusCommandWriter(List<ModbusSlaveConfig> slaves, Map<String, ModbusTcpClient> clients,
            ConnectorContext ctx) {
        this.slaves = slaves;
        this.clients = clients;
        this.ctx = ctx;
    }

    /** 处理一条写命令；deviceKey 不属于任何 slave 时静默忽略 */
    public void onCommand(String deviceKey, Map<String, Object> data, String requestId) {
        ModbusSlaveConfig slave = slaves.stream()
                .filter(s -> s.deviceKey().equals(deviceKey))
                .findFirst()
                .orElse(null);
        if (slave == null) {
            return;
        }
        try {
            if (data.get("writeRegister") instanceof Map<?, ?> m) {
                writeRegister(slave, Configs.castMap(m), requestId);
            } else if (data.get("writeCoil") instanceof Map<?, ?> m) {
                writeCoil(slave, Configs.castMap(m), requestId);
            } else {
                ctx.reply(deviceKey, requestId, Map.of("error", "expected writeRegister/writeCoil"));
            }
        } catch (Exception e) {
            ctx.reply(deviceKey, requestId, Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private void writeRegister(ModbusSlaveConfig slave, Map<String, Object> cmd, String requestId) throws Exception {
        ModbusSlaveConfig.RegisterDef def = findRegister(slave, cmd);
        ModbusTcpClient client = clients.get(slave.deviceKey());
        if (def == null || client == null) {
            ctx.reply(slave.deviceKey(), requestId, Map.of("error", "unknown register name"));
            return;
        }
        double value = Configs.doubleOf(cmd, "value", 0);
        if (def.isWide()) {
            byte[] wide = ModbusValueCodec.encodeWide(value, def.dataType());
            client.writeMultipleRegisters(slave.unitId(),
                    new WriteMultipleRegistersRequest(def.address(), wide.length / 2, wide));
        } else {
            client.writeSingleRegister(slave.unitId(),
                    new WriteSingleRegisterRequest(def.address(), ModbusValueCodec.encodeRegister16(value)));
        }
        ctx.reply(slave.deviceKey(), requestId, Map.of("status", "ok"));
    }

    private void writeCoil(ModbusSlaveConfig slave, Map<String, Object> cmd, String requestId) throws Exception {
        ModbusSlaveConfig.RegisterDef def = findRegister(slave, cmd);
        ModbusTcpClient client = clients.get(slave.deviceKey());
        if (def == null || client == null) {
            ctx.reply(slave.deviceKey(), requestId, Map.of("error", "unknown coil name"));
            return;
        }
        client.writeSingleCoil(slave.unitId(),
                new WriteSingleCoilRequest(def.address(), ModbusValueCodec.encodeCoil(cmd.get("value"))));
        ctx.reply(slave.deviceKey(), requestId, Map.of("status", "ok"));
    }

    private static ModbusSlaveConfig.RegisterDef findRegister(ModbusSlaveConfig slave, Map<String, Object> cmd) {
        String name = Configs.stringOf(cmd, "name", null);
        return slave.registers().stream().filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }
}
