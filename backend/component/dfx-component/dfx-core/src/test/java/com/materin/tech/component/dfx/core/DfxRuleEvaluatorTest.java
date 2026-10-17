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
package com.materin.tech.component.dfx.core;

import com.materin.tech.common.spi.DfxAlertEvent;
import com.materin.tech.common.spi.DfxAlertSink;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.dfx.core.entity.DfxAlertRule;
import com.materin.tech.component.dfx.core.mapper.DfxAlertRuleMapper;
import com.materin.tech.component.dfx.core.mapper.DfxMetricConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** DFX 规则引擎 v2 单测：比较符矩阵 / duration 状态机 / 恢复条件 / 抑制窗口。 */
@ExtendWith(MockitoExtension.class)
class DfxRuleEvaluatorTest {

    @Mock DfxAlertRuleMapper ruleMapper;
    @Mock DfxMetricConfigMapper metricMapper;
    @Mock DfxAlertSink alertSink;
    @Mock DfxNotifyService notifyService;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private DfxRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ObjectProvider<DfxAlertSink> sink = Mockito.mock(ObjectProvider.class);
        lenient().when(sink.getIfAvailable()).thenReturn(alertSink);
        evaluator = new DfxRuleEvaluator(ruleMapper, metricMapper, sink, notifyService, redis);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(ruleMapper.selectListByQuery(any())).thenReturn(List.of());
        lenient().when(metricMapper.selectListByQuery(any())).thenReturn(List.of());
        lenient().when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true);
    }

    private DeviceCredentialLookup.DeviceCredential cred() {
        return new DeviceCredentialLookup.DeviceCredential(
                "th-001", 1L, "secret", "测试设备");
    }

    private DfxAlertRule rule(String metric, String cmp, double threshold) {
        DfxAlertRule r = new DfxAlertRule();
        r.setId(11L);
        r.setDeviceId(1L);
        r.setMetric(metric);
        r.setComparator(cmp);
        r.setThreshold(threshold);
        r.setLevel("warn");
        r.setSuppressSeconds(600);
        r.setEnabled(1);
        return r;
    }

    // ------------------------------------------------------------ matches 矩阵（静态方法直测）

    @Test
    @DisplayName("比较符矩阵：gt/gte/lt/lte/eq/ne/between 全覆盖")
    void matches_shouldCoverAllComparators() {
        Assertions.assertTrue(DfxRuleEvaluator.matches("gt", 90.0, null, 95.0));
        Assertions.assertTrue(DfxRuleEvaluator.matches("gte", 90.0, null, 90.0));
        Assertions.assertTrue(DfxRuleEvaluator.matches("lt", 10.0, null, 5.0));
        Assertions.assertTrue(DfxRuleEvaluator.matches("lte", 10.0, null, 10.0));
        Assertions.assertTrue(DfxRuleEvaluator.matches("eq", 42.0, null, 42.0));
        Assertions.assertTrue(DfxRuleEvaluator.matches("ne", 42.0, null, 41.0));
        Assertions.assertTrue(DfxRuleEvaluator.matches("between", 10.0, 20.0, 15.0));
        Assertions.assertFalse(DfxRuleEvaluator.matches("between", 10.0, 20.0, 25.0));
        Assertions.assertFalse(DfxRuleEvaluator.matches("gt", 90.0, null, 85.0));
    }

    // ------------------------------------------------------------ duration 状态机

    @Test
    @DisplayName("duration=60s：首样本只记状态不告警，越限持续过窗口才告警")
    void duration_shouldRequireSustainedViolation() {
        DfxAlertRule r = rule("cpu_usage_pct", "gt", 90);
        r.setDurationSeconds(60);
        Mockito.when(ruleMapper.selectListByQuery(any())).thenReturn(List.of(r));
        evaluator.evaluate(cred(), null, 0L, Map.of("cpu_usage_pct", 95));
        verify(alertSink, never()).raise(any(DfxAlertEvent.class));
        evaluator.evaluate(cred(), null, 30_000L, Map.of("cpu_usage_pct", 95));
        verify(alertSink, never()).raise(any(DfxAlertEvent.class));
        evaluator.evaluate(cred(), null, 61_000L, Map.of("cpu_usage_pct", 95));
        verify(alertSink).raise(any(DfxAlertEvent.class));
    }

    @Test
    @DisplayName("越限中断重置持续计时（恢复后再越限重新计时）")
    void duration_shouldResetOnInterruption() {
        DfxAlertRule r = rule("cpu_usage_pct", "gt", 90);
        r.setDurationSeconds(60);
        Mockito.when(ruleMapper.selectListByQuery(any())).thenReturn(List.of(r));
        evaluator.evaluate(cred(), null, 0L, Map.of("cpu_usage_pct", 95));
        evaluator.evaluate(cred(), null, 30_000L, Map.of("cpu_usage_pct", 50));
        evaluator.evaluate(cred(), null, 30_500L, Map.of("cpu_usage_pct", 95));
        verify(alertSink, never()).raise(any(DfxAlertEvent.class));
    }

    // ------------------------------------------------------------ 恢复与抑制

    @Test
    @DisplayName("恢复条件满足时清除活跃告警（clear 经 DfxAlertSink.clear）")
    void clear_shouldClearActiveAlertWhenConditionMet() {
        DfxAlertRule r = rule("cpu_usage_pct", "gt", 90);
        r.setClearComparator("lt");
        r.setClearThreshold(70.0);
        Mockito.when(ruleMapper.selectListByQuery(any())).thenReturn(List.of(r));
        evaluator.evaluate(cred(), null, 0L, Map.of("cpu_usage_pct", 95));
        verify(alertSink).raise(any(DfxAlertEvent.class));
        Mockito.clearInvocations(alertSink);
        evaluator.evaluate(cred(), null, 1000L, Map.of("cpu_usage_pct", 60));
        verify(alertSink).clear(eq(11L), isNull(), eq(1L), any());
    }

    @Test
    @DisplayName("抑制窗口内重复越限不重复告警（Redis SETNX 拒绝）")
    void suppress_shouldBlockRepeatAlerts() {
        DfxAlertRule r = rule("cpu_usage_pct", "gt", 90);
        Mockito.when(ruleMapper.selectListByQuery(any())).thenReturn(List.of(r));
        Mockito.when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(false);
        evaluator.evaluate(cred(), null, 0L, Map.of("cpu_usage_pct", 95));
        verify(alertSink, never()).raise(any(DfxAlertEvent.class));
    }

    @Test
    @DisplayName("Redis 故障退化为不抑制（fail-open，不丢告警）")
    void degradation_shouldDegradeToNoSuppress() {
        DfxAlertRule r = rule("cpu_usage_pct", "gt", 90);
        Mockito.when(ruleMapper.selectListByQuery(any())).thenReturn(List.of(r));
        Mockito.when(valueOps.setIfAbsent(any(), any(), any())).thenThrow(new RuntimeException("redis down"));
        evaluator.evaluate(cred(), null, 0L, Map.of("cpu_usage_pct", 95));
        verify(alertSink).raise(any(DfxAlertEvent.class));
    }
}
