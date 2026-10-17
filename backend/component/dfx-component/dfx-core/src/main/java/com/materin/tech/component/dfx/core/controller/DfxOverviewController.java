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
package com.materin.tech.component.dfx.core.controller;

import com.materin.tech.common.core.R;
import com.materin.tech.common.spi.DfxAlertSink;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.dfx.core.health.DfxAccessHealth;
import com.materin.tech.component.dfx.core.health.DfxHealthRegistry;
import com.materin.tech.component.dfx.core.health.DfxAccessHealthProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** DFX 健康大盘接口：/api/v1/device/dfx/overview。 */
@Tag(name = "DFX 健康大盘")
@RestController
@RequestMapping("/device/dfx")
@RequiredArgsConstructor
public class DfxOverviewController {

    private final ObjectProvider<DfxAccessHealthProvider> healthProviders;
    private final DfxHealthRegistry healthRegistry;
    private final ObjectProvider<DfxAlertSink> alertSink;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;

    @Operation(summary = "健康大盘（设备在线统计 + 告警生命周期统计 + 趋势 + 接入组件健康）")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview(@RequestParam(defaultValue = "7") int trendDays) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 设备在线统计（SPI）
        var cred = credentialLookup.getIfAvailable();
        if (cred != null) {
            try {
                DeviceCredentialLookup.DeviceStatusStats st = cred.statusStats();
                Map<String, Object> devices = new LinkedHashMap<>();
                devices.put("total", st.total());
                devices.put("online", st.online());
                devices.put("offline", st.offline());
                devices.put("inactive", st.inactive());
                result.put("devices", devices);
            } catch (Exception e) {
                result.put("devices", Map.of("error", String.valueOf(e.getMessage())));
            }
        }

        // 告警生命周期统计 + 近 N 天趋势（SPI）
        var sink = alertSink.getIfAvailable();
        if (sink != null) {
            try {
                result.put("alerts", sink.stats());
                result.put("trend", sink.trend(trendDays));
            } catch (Exception e) {
                result.put("alerts", Map.of("error", String.valueOf(e.getMessage())));
            }
        }

        // 接入组件健康（复用 /health 的组装逻辑）
        java.util.List<DfxAccessHealth> components = new ArrayList<>();
        for (DfxAccessHealthProvider p : healthProviders) {
            try {
                components.add(p.health());
            } catch (Exception ignore) {
                // 健康探针异常不阻塞接口
            }
        }
        result.put("components", components);
        result.put("pipeline", healthRegistry.sourceStats());
        return R.ok(result);
    }
}
