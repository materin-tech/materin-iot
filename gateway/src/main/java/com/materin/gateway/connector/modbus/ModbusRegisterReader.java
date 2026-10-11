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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadCoilsResponse;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersResponse;
import com.materin.gateway.core.ConnectorContext;

/**
 * Modbus 读路径：轮询单个 slave 的全部寄存器并合并为一次遥测上报。
 * coils 读布尔，其余按 data-type + scale 解码；单寄存器失败不影响同批其余寄存器。
 */
public class ModbusRegisterReader {

    private final List<ModbusSlaveConfig> slaves;
    private final Map<String, ModbusTcpClient> clients;
    private final ConnectorContext ctx;

    public ModbusRegisterReader(List<ModbusSlaveConfig> slaves, Map<String, ModbusTcpClient> clients,
            ConnectorContext ctx) {
        this.slaves = slaves;
        this.clients = clients;
        this.ctx = ctx;
    }

    /** 轮询单个 slave；连接断开时先重连，全部读完后一次性上报遥测 */
    public void pollSlave(ModbusSlaveConfig slave) {
        try {
            ModbusTcpClient client = clients.get(slave.deviceKey());
            if (!client.isConnected()) {
                client.connect();
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (ModbusSlaveConfig.RegisterDef register : slave.registers()) {
                try {
                    readRegister(client, slave, register, values);
                } catch (Exception e) {
                    // 单个寄存器读取失败不阻断同批其余寄存器
                }
            }
            if (!values.isEmpty()) {
                ctx.telemetry(slave.deviceKey(), values);
            }
        } catch (Exception e) {
            // 轮询失败降级：等待下一周期重试
        }
    }

    private void readRegister(ModbusTcpClient client, ModbusSlaveConfig slave,
            ModbusSlaveConfig.RegisterDef register, Map<String, Object> values) throws Exception {
        int quantity = register.isWide() ? 2 : 1;
        byte[] data = switch (register.functionCode()) {
            case "readInputRegisters" -> client.readInputRegisters(slave.unitId(),
                    new ReadInputRegistersRequest(register.address(), quantity)).registers();
            case "readCoils" -> client.readCoils(slave.unitId(),
                    new ReadCoilsRequest(register.address(), quantity)).coils();
            default -> client.readHoldingRegisters(slave.unitId(),
                    new ReadHoldingRegistersRequest(register.address(), quantity)).registers();
        };
        if ("readCoils".equals(register.functionCode())) {
            values.put(register.name(), ModbusValueCodec.decodeCoil(data));
        } else {
            values.put(register.name(), ModbusValueCodec.decode(data, register.dataType(), register.scale()));
        }
    }
}
