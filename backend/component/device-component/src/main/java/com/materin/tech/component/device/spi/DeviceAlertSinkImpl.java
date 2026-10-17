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
package com.materin.tech.component.device.spi;

import com.materin.tech.common.spi.DfxAlertEvent;
import com.materin.tech.common.spi.DfxAlertSink;
import com.materin.tech.component.device.entity.DeviceAlert;
import com.materin.tech.component.device.mapper.DeviceAlertMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DfxAlertSink 实现：DFX 告警落 device_alert 表（表归属设备域），带生命周期语义——
 * 同规则（ruleId，非规则告警按 metric）活跃告警合并累加而非重复插行；clear 置已恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAlertSinkImpl implements DfxAlertSink {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 状态：0-活跃 1-已确认 2-已恢复 */
    static final int ST_ACTIVE = 0;
    static final int ST_ACKED = 1;
    static final int ST_CLEARED = 2;

    private final DeviceAlertMapper alertMapper;

    @Override
    public RaiseResult raise(DfxAlertEvent event) {
        try {
            DeviceAlert active = findActive(event.ruleId(), event.metric(), event.deviceId());
            LocalDateTime now = LocalDateTime.now();
            if (active != null) {
                active.setLevel(event.level());
                active.setContent(event.content());
                active.setLastValue(event.value());
                active.setTriggerCount(active.getTriggerCount() == null ? 2 : active.getTriggerCount() + 1);
                active.setLastTime(now);
                alertMapper.update(active);
                return RaiseResult.of(active.getId(), false);
            }
            DeviceAlert alert = new DeviceAlert();
            alert.setDeviceId(event.deviceId());
            alert.setDeviceName(event.deviceName());
            alert.setLevel(event.level());
            alert.setContent(event.content());
            alert.setRuleId(event.ruleId());
            alert.setMetric(event.metric());
            alert.setLastValue(event.value());
            alert.setTriggerCount(1);
            alert.setLastTime(now);
            alert.setTraceId(com.materin.tech.common.core.TraceIds.current());
            alert.setStatus(ST_ACTIVE);
            alertMapper.insert(alert);
            return RaiseResult.of(alert.getId(), true);
        } catch (Exception e) {
            log.warn("DFX 告警落库失败（忽略）: deviceId={}, {}", event.deviceId(), e.getMessage());
            return RaiseResult.of(null, false);
        }
    }

    @Override
    public boolean clear(Long ruleId, String metric, Long deviceId, String reason) {
        try {
            DeviceAlert active = findActive(ruleId, metric, deviceId);
            if (active == null) {
                return false;
            }
            active.setStatus(ST_CLEARED);
            active.setClearTime(LocalDateTime.now());
            active.setClearReason(reason);
            alertMapper.update(active);
            return true;
        } catch (Exception e) {
            log.warn("DFX 告警恢复失败（忽略）: deviceId={}, {}", deviceId, e.getMessage());
            return false;
        }
    }

    /** 活跃告警合并键：规则告警按 ruleId，非规则告警按 metric。 */
    private DeviceAlert findActive(Long ruleId, String metric, Long deviceId) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("device_id", deviceId)
                .eq("status", ST_ACTIVE);
        if (ruleId != null) {
            qw.eq("rule_id", ruleId);
        } else {
            qw.eq("metric", metric == null ? "manual" : metric);
        }
        return alertMapper.selectOneByQuery(qw);
    }

    @Override
    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", count(null));
        stats.put("active", count(ST_ACTIVE));
        stats.put("acked", count(ST_ACKED));
        stats.put("cleared", count(ST_CLEARED));
        stats.put("info", countByLevel("info"));
        stats.put("warn", countByLevel("warn"));
        stats.put("error", countByLevel("error"));
        return stats;
    }

    @Override
    public Map<String, Long> trend(int days) {
        Map<String, Long> trend = new LinkedHashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<DeviceAlert> alerts = alertMapper.selectListByQuery(QueryWrapper.create()
                .ge("create_time", since));
        for (int i = days - 1; i >= 0; i--) {
            trend.put(LocalDateTime.now().minusDays(i).format(DAY), 0L);
        }
        for (DeviceAlert a : alerts) {
            String day = a.getCreateTime() == null ? "" : a.getCreateTime().format(DAY);
            trend.merge(day, 1L, Long::sum);
        }
        return trend;
    }

    private Long count(Integer status) {
        QueryWrapper qw = QueryWrapper.create();
        if (status != null) {
            qw.eq("status", status);
        }
        Long c = alertMapper.selectCountByQuery(qw);
        return c == null ? 0L : c;
    }

    private Long countByLevel(String level) {
        Long c = alertMapper.selectCountByQuery(QueryWrapper.create().eq("level", level));
        return c == null ? 0L : c;
    }
}
