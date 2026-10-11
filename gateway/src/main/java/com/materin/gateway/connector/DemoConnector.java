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
package com.materin.gateway.connector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.materin.gateway.core.Connector;
import com.materin.gateway.core.ConnectorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 演示 connector（type: demo）：按配置周期为虚拟子设备生成随机温湿度遥测；
 * 命令 {"set": {...}} 固定遥测值（先回执再改值）；忽略不认识的 deviceKey。
 */
@Slf4j
public class DemoConnector implements Connector {

    private static final long DEFAULT_INTERVAL_MS = 10_000L;

    private final String id;
    private final long intervalMs;
    private final List<SubDevice> devices = new ArrayList<>();
    private final Map<String, Map<String, Object>> overrides = new ConcurrentHashMap<>();
    private final java.util.Random random = new java.util.Random();
    private volatile ConnectorContext ctx;
    private ScheduledExecutorService scheduler;

    public DemoConnector(Map<String, Object> cfg) {
        this.id = Configs.stringOf(cfg, "id", "demo");
        this.intervalMs = Configs.longOf(cfg, "interval-ms", DEFAULT_INTERVAL_MS);
        parseDevices(cfg.get("devices"));
    }

    private void parseDevices(Object raw) {
        for (Object item : Configs.listOf(raw)) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> map = Configs.castMap(m);
                String deviceKey = Configs.stringOf(map, "device-key", null);
                if (deviceKey != null && !deviceKey.isBlank()) {
                    devices.add(new SubDevice(deviceKey, Configs.stringOf(map, "name", deviceKey),
                            Configs.stringOf(map, "product-key", null)));
                }
            }
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return "demo";
    }

    @Override
    public void start(ConnectorContext ctx) {
        this.ctx = ctx;
        for (SubDevice device : devices) {
            ctx.connect(device.deviceKey(), device.name(), device.productKey());
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "demo-connector-" + id);
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::reportTelemetry, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void reportTelemetry() {
        for (SubDevice device : devices) {
            try {
                Map<String, Object> values = new java.util.LinkedHashMap<>();
                values.put("temp", Math.round((15 + random.nextDouble() * 15) * 10) / 10.0);
                values.put("humidity", Math.round((40 + random.nextDouble() * 20) * 10) / 10.0);
                Map<String, Object> override = overrides.get(device.deviceKey());
                if (override != null) {
                    values.putAll(override);
                }
                ctx.telemetry(device.deviceKey(), values);
            } catch (Exception e) {
                log.warn("demo 遥测生成失败 deviceKey={}: {}", device.deviceKey(), e.getMessage());
            }
        }
    }

    @Override
    public void onCommand(String deviceKey, Map<String, Object> data, String requestId) {
        if (devices.stream().noneMatch(d -> d.deviceKey().equals(deviceKey))) {
            return;
        }
        Object set = data.get("set");
        if (set instanceof Map<?, ?> map) {
            ctx.reply(deviceKey, requestId, Map.of("status", "ok"));
            overrides.computeIfAbsent(deviceKey, k -> new java.util.concurrent.ConcurrentHashMap<>())
                    .putAll(Configs.castMap(map));
            log.info("[{}] 子设备 {} 遥测值已固定: {}", id, deviceKey, set);
        } else {
            ctx.reply(deviceKey, requestId, Map.of("error", "unsupported command, expected {\"set\":{...}}"));
        }
    }

    @Override
    public Set<String> devices() {
        return devices.stream().map(SubDevice::deviceKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private record SubDevice(String deviceKey, String name, String productKey) {
    }
}
