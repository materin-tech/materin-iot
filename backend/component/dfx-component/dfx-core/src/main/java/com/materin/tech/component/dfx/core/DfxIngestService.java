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

import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.DfxSink;
import com.materin.tech.component.dfx.core.entity.DfxAlertRule;
import com.materin.tech.component.dfx.core.health.DfxHealthRegistry;
import com.materin.tech.component.dfx.core.mapper.DfxAlertRuleMapper;
import com.materin.tech.component.timeseries.TimeSeriesColumn;
import com.materin.tech.component.timeseries.TimeSeriesData;
import com.materin.tech.component.timeseries.TimeSeriesManager;
import com.materin.tech.component.timeseries.TimeSeriesMetadata;
import com.materin.tech.component.timeseries.TimeSeriesQuery;
import com.materin.tech.component.timeseries.ValueType;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DFX 统一接入管道（dfx-core 核心，docs/dfx-monitoring-design.md §1.1/§5）：
 * 协议适配器把报文解析为 DfxRecord（KV 指标）后调用本服务——
 * KV 校验 → Redis 最新值 → IoTDB（dfx_ 前缀列）→ 阈值告警评估 → 健康统计。
 * 全链路兜底异常：指标管道故障只降级，绝不阻断接入链路。
 */
@Slf4j
@Component
public class DfxIngestService implements DfxSink {

    /** 时序模型标识：与属性/事件/方法并列的第四类设备维度模型 */
    public static final String METRIC = "device_dfx";

    /** 标准数值列（列名 = dfx_ + 指标名） */
    static final List<TimeSeriesColumn> STANDARD_COLUMNS = List.of(
            TimeSeriesColumn.of("dfx_cpu_usage_pct", ValueType.DOUBLE),
            TimeSeriesColumn.of("dfx_mem_usage_pct", ValueType.DOUBLE),
            TimeSeriesColumn.of("dfx_swap_usage_pct", ValueType.DOUBLE),
            TimeSeriesColumn.of("dfx_disk_usage_pct", ValueType.DOUBLE),
            TimeSeriesColumn.of("dfx_load_1m", ValueType.DOUBLE),
            TimeSeriesColumn.of("dfx_net_rx_kbps", ValueType.DOUBLE),
            TimeSeriesColumn.of("dfx_net_tx_kbps", ValueType.DOUBLE),
            TimeSeriesColumn.of("dfx_uptime_s", ValueType.LONG),
            TimeSeriesColumn.of("dfx_heartbeat", ValueType.LONG));

    private static final long DEFAULT_SUPPRESS_SECONDS = 600L;

    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final DfxRuleEvaluator ruleEvaluator;
    private final ObjectProvider<TimeSeriesManager> managerProvider;
    private final StringRedisTemplate redis;
    private final DfxHealthRegistry healthRegistry;

    public DfxIngestService(ObjectProvider<DeviceCredentialLookup> credentialLookup,
                            DfxRuleEvaluator ruleEvaluator,
                            ObjectProvider<TimeSeriesManager> managerProvider,
                            StringRedisTemplate redis,
                            DfxHealthRegistry healthRegistry) {
        this.credentialLookup = credentialLookup;
        this.ruleEvaluator = ruleEvaluator;
        this.managerProvider = managerProvider;
        this.redis = redis;
        this.healthRegistry = healthRegistry;
    }

    /** 统一入口：协议适配器传入 DfxRecord（KV）。 */
    public void ingest(DfxRecord record) {
        saveDfx(record.deviceKey(), record.time(), record.source(), record.metrics());
    }

    @Override
    public void saveDfx(String deviceKey, long time, String source, Map<String, Object> metrics) {
        try {
            if (metrics == null || metrics.isEmpty()) {
                return;
            }
            DeviceCredentialLookup lookup = credentialLookup.getIfAvailable();
            if (lookup == null) {
                return;
            }
            DeviceCredentialLookup.DeviceCredential cred = lookup.findByKey(deviceKey).orElse(null);
            if (cred == null) {
                log.debug("DFX 上报设备不存在（忽略）: {}", deviceKey);
                return;
            }
            Map<String, Object> keyed = keyByDfxPrefix(metrics);
            long ts = time <= 0 ? System.currentTimeMillis() : time;
            storeLatest(deviceKey, source, keyed);
            saveTimeSeries(deviceKey, cred.deviceId(), ts, keyed);
            evaluateRules(cred, ts, metrics);
            healthRegistry.recordIngest(source);
        } catch (Exception e) {
            log.warn("DFX 指标入库失败（忽略）: deviceKey={}, source={}, {}",
                    deviceKey, source, e.getMessage());
        }
    }

    /** 指标键加 dfx_ 前缀（IoTDB 测量名不允许点号；统一前缀便于结果过滤）。 */
    private Map<String, Object> keyByDfxPrefix(Map<String, Object> metrics) {
        Map<String, Object> keyed = new LinkedHashMap<>();
        metrics.forEach((k, v) -> {
            if (k != null && !k.isBlank()) {
                keyed.put(k.startsWith("dfx_") ? k : "dfx_" + k, v);
            }
        });
        return keyed;
    }

    private void storeLatest(String deviceKey, String source, Map<String, Object> keyed) {
        try {
            String key = RedisKeys.DEVICE_DFX + deviceKey;
            keyed.forEach((k, v) -> redis.opsForHash().put(key, k, String.valueOf(v)));
            redis.opsForHash().put(key, "source", source);
            redis.opsForHash().put(key, "time", String.valueOf(System.currentTimeMillis()));
            redis.expire(key, Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("DFX 最新值写入失败: deviceKey={}, {}", deviceKey, e.getMessage());
        }
    }

    private void saveTimeSeries(String deviceKey, Long deviceId, long time,
                                Map<String, Object> keyed) {
        TimeSeriesManager manager = managerProvider.getIfAvailable();
        Long productId = credentialLookup.getIfAvailable() == null
                ? null : credentialLookup.getIfAvailable().findProductIdByKey(deviceKey).orElse(null);
        if (manager == null || productId == null) {
            return;
        }
        TimeSeriesMetadata metadata = new TimeSeriesMetadata(
                METRIC, String.valueOf(productId), STANDARD_COLUMNS);
        manager.getTimeSeries(metadata).save(
                new TimeSeriesData(String.valueOf(deviceId), time, keyed));
    }

    // ------------------------------------------------------------ 阈值告警

    /** 同步评估启用中的规则（委托 DfxRuleEvaluator：比较符/持续时长/恢复条件/抑制窗口/通知分发）。 */
    private void evaluateRules(DeviceCredentialLookup.DeviceCredential cred,
                               long time, Map<String, Object> metrics) {
        Long productId = credentialLookup.getIfAvailable() == null ? null
                : credentialLookup.getIfAvailable().findProductIdByKey(cred.deviceKey()).orElse(null);
        ruleEvaluator.evaluate(cred, productId, time, metrics);
    }

    // ------------------------------------------------------------ 查询

    /** DFX 历史查询（dfx_ 前缀过滤，供 REST 层调用）。 */
    public List<Map<String, Object>> queryHistory(Long deviceId, TimeSeriesQuery query) {
        Long productId = credentialLookup.getIfAvailable() == null
                ? null
                : credentialLookup.getIfAvailable()
                        .findProductIdByKey(
                                credentialLookup.getIfAvailable()
                                        .findKeyById(deviceId).orElse("_"))
                        .orElse(null);
        if (productId == null) {
            return List.of();
        }
        TimeSeriesManager manager = managerProvider.getIfAvailable();
        if (manager == null) {
            return List.of();
        }
        TimeSeriesMetadata metadata = new TimeSeriesMetadata(
                METRIC, String.valueOf(productId), STANDARD_COLUMNS);
        return manager.getTimeSeries(metadata).query(
                TimeSeriesQuery.builder(String.valueOf(deviceId))
                        .start(query.start()).end(query.end())
                        .keys(query.keys()).keyPrefix("dfx_")
                        .limit(query.limit()).desc(query.desc())
                        .aggregation(query.aggFunction(), query.aggIntervalMs())
                        .build());
    }

    /** Redis 最新值（含 source/time）。 */
    public Map<String, Object> latestOf(String deviceKey) {
        Map<Object, Object> entries;
        try {
            entries = redis.opsForHash().entries(RedisKeys.DEVICE_DFX + deviceKey);
        } catch (Exception e) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        entries.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
