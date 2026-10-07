package com.materin.tech.component.dfx.core;

import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.DfxAlertSink;
import com.materin.tech.component.dfx.core.entity.DfxAlertRule;
import com.materin.tech.component.dfx.core.health.DfxHealthRegistry;
import com.materin.tech.component.dfx.core.mapper.DfxAlertRuleMapper;
import com.materin.tech.component.timeseries.TimeSeriesManager;
import com.materin.tech.component.timeseries.TimeSeriesMetadata;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** DFX 统一管道单测（dfx-core）：KV 键映射 / 阈值评估 / 抑制窗口 / 未知设备容错。 */
@ExtendWith(MockitoExtension.class)
class DfxIngestServiceTest {

    @Mock DeviceCredentialLookup credentialLookup;
    @Mock DfxAlertSink alertSink;
    @Mock DfxAlertRuleMapper ruleMapper;
    @Mock StringRedisTemplate redis;
    @Mock HashOperations<String, Object, Object> hashOps;

    private DfxIngestService service;

    @BeforeEach
    void setUp() {
        ObjectProvider<DeviceCredentialLookup> cl = Mockito.mock(ObjectProvider.class);
        lenient().when(cl.getIfAvailable()).thenReturn(credentialLookup);
        ObjectProvider<DfxAlertSink> as = Mockito.mock(ObjectProvider.class);
        lenient().when(as.getIfAvailable()).thenReturn(alertSink);
        ObjectProvider<TimeSeriesManager> tm = Mockito.mock(ObjectProvider.class);
        lenient().when(tm.getIfAvailable()).thenReturn(null);
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        service = new DfxIngestService(cl, as, ruleMapper, tm, redis, new DfxHealthRegistry());
    }

    private DeviceCredentialLookup.DeviceCredential cred() {
        return new DeviceCredentialLookup.DeviceCredential(
                "th-001", 1L, "secret", "测试设备");
    }

    private DfxAlertRule rule(String metric, String cmp, double threshold) {
        DfxAlertRule r = new DfxAlertRule();
        r.setDeviceId(1L);
        r.setMetric(metric);
        r.setComparator(cmp);
        r.setThreshold(threshold);
        r.setLevel("warn");
        r.setSuppressSeconds(600);
        r.setEnabled(1);
        return r;
    }

    @BeforeEach
    void stubCommon() {
        lenient().when(credentialLookup.findByKey("th-001")).thenReturn(java.util.Optional.of(cred()));
        lenient().when(credentialLookup.findByKey("ghost")).thenReturn(java.util.Optional.empty());
        lenient().when(credentialLookup.findProductIdByKey("th-001")).thenReturn(java.util.Optional.of(7L));
        lenient().when(ruleMapper.selectListByQuery(any())).thenReturn(java.util.List.of());
    }

    @Test
    @DisplayName("dfx_ 前缀键映射：KV 指标进 Redis 最新值 HASH")
    void ingest_shouldKeyMetricsWithDfxPrefix() {
        service.ingest(new DfxRecord("th-001", 1000L, "http", Map.of("cpu_usage_pct", 33.3)));
        verify(hashOps).put(any(), eq("dfx_cpu_usage_pct"), eq("33.3"));
        verify(hashOps).put(any(), eq("source"), eq("http"));
    }

    @Test
    @DisplayName("cpu 越限触发告警经 DfxAlertSink 落库")
    void ingest_shouldRaiseAlertWhenThresholdExceeded() {
        Mockito.when(ruleMapper.selectListByQuery(any()))
                .thenReturn(java.util.List.of(rule("cpu_usage_pct", "gt", 90)));
        service.ingest(new DfxRecord("th-001", 1000L, "mqtt", Map.of("cpu_usage_pct", 95.2)));
        verify(alertSink).raise(eq(1L), eq("测试设备"), eq("warn"), any());
    }

    @Test
    @DisplayName("抑制窗口内第二帧越限不重复告警")
    void ingest_shouldSuppressRepeatedAlerts() {
        Mockito.when(ruleMapper.selectListByQuery(any()))
                .thenReturn(java.util.List.of(rule("cpu_usage_pct", "gt", 90)));
        service.ingest(new DfxRecord("th-001", 1000L, "mqtt", Map.of("cpu_usage_pct", 95.2)));
        service.ingest(new DfxRecord("th-001", 2000L, "mqtt", Map.of("cpu_usage_pct", 96.0)));
        verify(alertSink, Mockito.times(1)).raise(any(), any(), any(), any());
    }

    @Test
    @DisplayName("lt 规则：低于阈值告警")
    void ingest_shouldSupportLtComparator() {
        Mockito.when(ruleMapper.selectListByQuery(any()))
                .thenReturn(java.util.List.of(rule("mem_usage_pct", "lt", 10)));
        service.ingest(new DfxRecord("th-001", 1000L, "snmp", Map.of("mem_usage_pct", 5)));
        verify(alertSink).raise(any(), any(), any(), any());
    }

    @Test
    @DisplayName("设备不存在时静默降级不抛异常")
    void ingest_shouldTolerateUnknownDevice() {
        service.ingest(new DfxRecord("ghost", 1000L, "http", Map.of("cpu_usage_pct", 1)));
        verify(alertSink, never()).raise(any(), any(), any(), any());
    }
}