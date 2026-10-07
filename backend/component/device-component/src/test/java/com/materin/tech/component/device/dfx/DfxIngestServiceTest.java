package com.materin.tech.component.device.dfx;

import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceAlertMapper;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.materin.tech.component.device.mapper.DfxAlertRuleMapper;
import com.materin.tech.component.timeseries.TimeSeriesManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.materin.tech.component.device.entity.DeviceAlert;
import com.materin.tech.component.device.entity.DfxAlertRule;

/** DFX 统一管道单测：键映射 / 阈值评估 / 抑制窗口 / 设备缺失容错。 */
@ExtendWith(MockitoExtension.class)
class DfxIngestServiceTest {

    @Mock DeviceMapper deviceMapper;
    @Mock DeviceAlertMapper alertMapper;
    @Mock DfxAlertRuleMapper ruleMapper;
    @Mock StringRedisTemplate redis;
    @Mock HashOperations<String, Object, Object> hashOps;

    private DfxIngestService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        service = new DfxIngestService(deviceMapper, alertMapper, ruleMapper, managerProvider(), redis);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<TimeSeriesManager> managerProvider() {
        ObjectProvider<TimeSeriesManager> p = Mockito.mock(ObjectProvider.class);
        lenient().when(p.getIfAvailable()).thenReturn(null); // 单测不依赖时序库
        return p;
    }

    private Device device() {
        Device d = new Device();
        d.setId(1L);
        d.setDeviceKey("th-001");
        d.setName("测试设备");
        d.setProductId(7L);
        return d;
    }

    private DfxAlertRule rule(String metric, String cmp, double threshold) {
        DfxAlertRule r = new DfxAlertRule();
        r.setId(9L);
        r.setDeviceId(1L);
        r.setDeviceName("测试设备");
        r.setMetric(metric);
        r.setComparator(cmp);
        r.setThreshold(threshold);
        r.setLevel("warn");
        r.setSuppressSeconds(600);
        r.setEnabled(1);
        return r;
    }

    @Test
    @DisplayName("dfx_ 前缀键映射：cpu_usage_pct → dfx_cpu_usage_pct，最新值进 Redis HASH")
    void saveDfx_shouldKeyMetricsWithDfxPrefix() {
        Mockito.when(deviceMapper.selectOneByQuery(any())).thenReturn(device());
        Mockito.when(ruleMapper.selectListByQuery(any())).thenReturn(java.util.List.of());
        service.saveDfx("th-001", 1000L, "http", Map.of("cpu_usage_pct", 33.3));
        verify(hashOps).put(anyString(), eq("dfx_cpu_usage_pct"), eq("33.3"));
        verify(hashOps).put(anyString(), eq("source"), eq("http"));
        verify(hashOps).put(anyString(), eq("time"), anyString());
    }

    @Test
    @DisplayName("cpu 越限触发告警落 device_alert")
    void saveDfx_shouldRaiseAlertWhenThresholdExceeded() {
        Mockito.when(deviceMapper.selectOneByQuery(any())).thenReturn(device());
        Mockito.when(ruleMapper.selectListByQuery(any())).thenReturn(
                java.util.List.of(rule("cpu_usage_pct", "gt", 90)));
        service.saveDfx("th-001", 1000L, "mqtt", Map.of("cpu_usage_pct", 95.2));
        verify(alertMapper).insert(any(DeviceAlert.class));
    }

    @Test
    @DisplayName("抑制窗口内第二帧越限不重复告警")
    void saveDfx_shouldSuppressRepeatedAlerts() {
        Mockito.when(deviceMapper.selectOneByQuery(any())).thenReturn(device());
        Mockito.when(ruleMapper.selectListByQuery(any())).thenReturn(
                java.util.List.of(rule("cpu_usage_pct", "gt", 90)));
        service.saveDfx("th-001", 1000L, "mqtt", Map.of("cpu_usage_pct", 95.2));
        service.saveDfx("th-001", 2000L, "mqtt", Map.of("cpu_usage_pct", 96.0));
        verify(alertMapper, Mockito.times(1)).insert(any(DeviceAlert.class));
    }

    @Test
    @DisplayName("lt 规则：低于阈值告警")
    void saveDfx_shouldSupportLtComparator() {
        Mockito.when(deviceMapper.selectOneByQuery(any())).thenReturn(device());
        Mockito.when(ruleMapper.selectListByQuery(any())).thenReturn(
                java.util.List.of(rule("mem_usage_pct", "lt", 10)));
        service.saveDfx("th-001", 1000L, "snmp", Map.of("mem_usage_pct", 5));
        verify(alertMapper).insert(any(DeviceAlert.class));
    }

    @Test
    @DisplayName("设备不存在时静默降级不抛异常")
    void saveDfx_shouldTolerateUnknownDevice() {
        Mockito.when(deviceMapper.selectOneByQuery(any())).thenReturn(null);
        service.saveDfx("ghost", 1000L, "http", Map.of("cpu_usage_pct", 1));
        verify(alertMapper, never()).insert(any(DeviceAlert.class));
    }
}
