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

import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.dfx.core.health.DfxHealthRegistry;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** DFX 统一管道单测（dfx-core v2）：KV 键映射 / 未知设备容错 / 规则评估委托。 */
@ExtendWith(MockitoExtension.class)
class DfxIngestServiceTest {

    @Mock DeviceCredentialLookup credentialLookup;
    @Mock DfxRuleEvaluator evaluator;
    @Mock StringRedisTemplate redis;
    @Mock HashOperations<String, Object, Object> hashOps;

    private DfxIngestService service;

    @BeforeEach
    void setUp() {
        ObjectProvider<DeviceCredentialLookup> cl = Mockito.mock(ObjectProvider.class);
        lenient().when(cl.getIfAvailable()).thenReturn(credentialLookup);
        ObjectProvider<TimeSeriesManager> tm = Mockito.mock(ObjectProvider.class);
        lenient().when(tm.getIfAvailable()).thenReturn(null);
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        service = new DfxIngestService(cl, evaluator, tm, redis, new DfxHealthRegistry());
    }

    private DeviceCredentialLookup.DeviceCredential cred() {
        return new DeviceCredentialLookup.DeviceCredential(
                "th-001", 1L, "secret", "测试设备");
    }

    @BeforeEach
    void stubCommon() {
        lenient().when(credentialLookup.findByKey("th-001")).thenReturn(Optional.of(cred()));
        lenient().when(credentialLookup.findByKey("ghost")).thenReturn(Optional.empty());
        lenient().when(credentialLookup.findProductIdByKey("th-001")).thenReturn(Optional.of(7L));
    }

    @Test
    @DisplayName("dfx_ 前缀键映射：KV 指标进 Redis 最新值 HASH")
    void ingest_shouldKeyMetricsWithDfxPrefix() {
        service.ingest(new DfxRecord("th-001", 1000L, "http", Map.of("cpu_usage_pct", 33.3)));
        verify(hashOps).put(any(), eq("dfx_cpu_usage_pct"), eq("33.3"));
        verify(hashOps).put(any(), eq("source"), eq("http"));
    }

    @Test
    @DisplayName("未知设备静默降级不抛异常")
    void ingest_shouldTolerateUnknownDevice() {
        service.ingest(new DfxRecord("ghost", 1000L, "http", Map.of("cpu_usage_pct", 1)));
        verify(evaluator, never()).evaluate(any(), any(), Mockito.anyLong(), any());
    }

    @Test
    @DisplayName("规则评估委托 DfxRuleEvaluator（设备+产品 ID 传递）")
    void ingest_shouldDelegateToEvaluator() {
        service.ingest(new DfxRecord("th-001", 1000L, "mqtt", Map.of("cpu_usage_pct", 95.2)));
        verify(evaluator).evaluate(eq(cred()), eq(7L), eq(1000L), any());
    }
}
