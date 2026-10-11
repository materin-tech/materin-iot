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
package com.materin.gateway.core;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.materin.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关自身遥测：以网关自己的 deviceKey 作为 device，
 * 走 gateway telemetry 主题周期上报 uptime / connector 数量等运行指标。
 */
@Slf4j
@Component
public class GatewaySelfReporter {

    private final GatewayService gateway;
    private final GatewayProperties properties;
    private final long startedAt = System.currentTimeMillis();

    public GatewaySelfReporter(GatewayService gateway, GatewayProperties properties) {
        this.gateway = gateway;
        this.properties = properties;
    }

    /** 周期上报网关自身运行指标（materin.gateway.report-interval-ms，默认 30s） */
    @Scheduled(fixedDelayString = "${materin.gateway.report-interval-ms:30000}")
    public void report() {
        String selfKey = properties.getPlatform().getDeviceKey();
        if (selfKey == null || selfKey.isBlank() || !gateway.isRunning()) {
            return;
        }
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("uptime", (System.currentTimeMillis() - startedAt) / 1000);
            values.put("connectors", gateway.connectorCount());
            gateway.telemetry(selfKey, values);
        } catch (Exception e) {
            log.warn("网关自身遥测上报失败: {}", e.getMessage());
        }
    }
}
