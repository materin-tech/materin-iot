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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.materin.gateway.connector.Configs;
import com.materin.gateway.core.Connector;
import com.materin.gateway.core.ConnectorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Modbus TCP connector（type: modbus-tcp）：配置驱动的 Modbus TCP 主站。
 * 生命周期与连接管理在此，读路径委托 {@link ModbusRegisterReader}，
 * 写路径（writeRegister/writeCoil）委托 {@link ModbusCommandWriter}。
 */
@Slf4j
public class ModbusTcpConnector implements Connector {

    private final String id;
    private final List<ModbusSlaveConfig> slaves;
    private final Map<String, ModbusTcpClient> clients = new ConcurrentHashMap<>();
    private volatile ConnectorContext ctx;
    private ModbusRegisterReader reader;
    private ModbusCommandWriter writer;
    private ScheduledExecutorService scheduler;

    public ModbusTcpConnector(Map<String, Object> cfg) {
        this.id = Configs.stringOf(cfg, "id", "modbus-tcp");
        this.slaves = ModbusSlaveConfig.parseSlaves(cfg.get("slaves"));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return "modbus-tcp";
    }

    @Override
    public Set<String> devices() {
        return slaves.stream().map(ModbusSlaveConfig::deviceKey).collect(Collectors.toUnmodifiableSet());
    }

    /** 启动：每个 slave 一条 TCP 连接 + 独立轮询周期 */
    @Override
    public void start(ConnectorContext ctx) {
        this.ctx = ctx;
        this.reader = new ModbusRegisterReader(slaves, clients, ctx);
        this.writer = new ModbusCommandWriter(slaves, clients, ctx);
        scheduler = Executors.newScheduledThreadPool(Math.max(1, slaves.size()), r -> {
            Thread thread = new Thread(r, "modbus-poll-" + id);
            thread.setDaemon(true);
            return thread;
        });
        for (ModbusSlaveConfig slave : slaves) {
            clients.put(slave.deviceKey(), ModbusTcpClient.create(NettyTcpClientTransport.create(
                    b -> b.setHostname(slave.host()).setPort(slave.port()))));
        }
        for (ModbusSlaveConfig slave : slaves) {
            scheduler.scheduleWithFixedDelay(
                    () -> reader.pollSlave(slave), slave.pollPeriodMs(), slave.pollPeriodMs(), TimeUnit.MILLISECONDS);
        }
    }

    /** 写命令入口：委托 ModbusCommandWriter 执行 */
    @Override
    public void onCommand(String deviceKey, Map<String, Object> data, String requestId) {
        writer.onCommand(deviceKey, data, requestId);
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        clients.values().forEach(client -> {
            try {
                client.disconnect();
            } catch (Exception e) {
                log.debug("[{}] 断开 Modbus 失败: {}", id, e.getMessage());
            }
        });
        clients.clear();
    }
}
