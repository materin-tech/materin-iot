package com.materin.tech.component.dfx.core;

import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.DfxAlertSink;
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
import java.util.concurrent.ConcurrentHashMap;

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
    private final ObjectProvider<DfxAlertSink> alertSink;
    private final DfxAlertRuleMapper ruleMapper;
    private final ObjectProvider<TimeSeriesManager> managerProvider;
    private final StringRedisTemplate redis;
    private final DfxHealthRegistry healthRegistry;
    private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();

    public DfxIngestService(ObjectProvider<DeviceCredentialLookup> credentialLookup,
                            ObjectProvider<DfxAlertSink> alertSink,
                            DfxAlertRuleMapper ruleMapper,
                            ObjectProvider<TimeSeriesManager> managerProvider,
                            StringRedisTemplate redis,
                            DfxHealthRegistry healthRegistry) {
        this.credentialLookup = credentialLookup;
        this.alertSink = alertSink;
        this.ruleMapper = ruleMapper;
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

    /** 同步评估启用中的规则；越限且不在抑制窗口则经 DfxAlertSink 落库。 */
    private void evaluateRules(DeviceCredentialLookup.DeviceCredential cred,
                               long time, Map<String, Object> metrics) {
        List<DfxAlertRule> rules = ruleMapper.selectListByQuery(
                QueryWrapper.create().eq("device_id", cred.deviceId()).eq("enabled", 1));
        for (DfxAlertRule rule : rules) {
            Object raw = metrics.get(rule.getMetric());
            if (raw == null) {
                continue;
            }
            try {
                double value = Double.parseDouble(String.valueOf(raw));
                boolean violated = "lt".equals(rule.getComparator())
                        ? value < rule.getThreshold() : value > rule.getThreshold();
                if (violated && notSuppressed(cred.deviceId(), rule)) {
                    raiseAlert(cred, rule, value);
                }
            } catch (NumberFormatException e) {
                log.debug("非数值指标跳过规则评估: {}={}", rule.getMetric(), raw);
            }
        }
    }

    private boolean notSuppressed(Long deviceId, DfxAlertRule rule) {
        String key = deviceId + ":" + rule.getMetric();
        long now = System.currentTimeMillis();
        long window = (rule.getSuppressSeconds() == null || rule.getSuppressSeconds() <= 0)
                ? DEFAULT_SUPPRESS_SECONDS : rule.getSuppressSeconds();
        Long last = lastAlertAt.get(key);
        if (last != null && now - last < window * 1000L) {
            return false;
        }
        lastAlertAt.put(key, now);
        return true;
    }

    private void raiseAlert(DeviceCredentialLookup.DeviceCredential cred,
                            DfxAlertRule rule, double value) {
        DfxAlertSink sink = alertSink.getIfAvailable();
        if (sink == null) {
            return;
        }
        String content = String.format("%s %.2f 超过阈值 %s %.0f（%ds 内不重复告警）",
                rule.getMetric(), value, "gt".equals(rule.getComparator()) ? ">" : "<",
                rule.getThreshold(),
                rule.getSuppressSeconds() == null ? 600 : rule.getSuppressSeconds());
        sink.raise(cred.deviceId(), cred.deviceName(),
                rule.getLevel() == null ? "warn" : rule.getLevel(), content);
        log.info("DFX 阈值告警: device={} {}={} {}", cred.deviceKey(),
                rule.getMetric(), value, rule.getLevel());
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
