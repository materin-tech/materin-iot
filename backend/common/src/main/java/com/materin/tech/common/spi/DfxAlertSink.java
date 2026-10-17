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
package com.materin.tech.common.spi;

import java.util.Map;

/**
 * DFX 告警出口 SPI：dfx-component 管道产生/恢复告警时调用，
 * 由 device-component 实现（device_alert 表归属设备域）。
 * 告警生命周期：0-活跃 → 1-已确认（人工） → 2-已恢复（条件满足自动）。
 * 实现方必须兜底异常，告警失败不影响指标管道。
 */
public interface DfxAlertSink {

    /**
     * 记录告警：同规则（ruleId，非规则告警按 metric）已有活跃告警则累加
     * 触发次数并刷新最近值，否则新建活跃告警。
     */
    RaiseResult raise(DfxAlertEvent event);

    /**
     * 规则恢复：将该规则（或 metric）+ 设备的活跃告警置为已恢复。
     *
     * @return 是否确实恢复了活跃告警（无可恢复对象返回 false）
     */
    boolean clear(Long ruleId, String metric, Long deviceId, String reason);

    /** 告警统计（大盘用）：total/active/acked/cleared/info/warn/error，默认空实现。 */
    default Map<String, Long> stats() {
        return Map.of();
    }

    /** 近 N 天告警趋势（date(yyyy-MM-dd) → count），默认空实现。 */
    default Map<String, Long> trend(int days) {
        return Map.of();
    }

    /** raise 结果：告警 ID 与是否新建（false=合并进已有活跃告警）。 */
    record RaiseResult(Long alertId, boolean created) {
        public static RaiseResult of(Long alertId, boolean created) {
            return new RaiseResult(alertId, created);
        }
    }
}
