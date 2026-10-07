package com.materin.tech.component.device.dfx;

import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.spi.DfxSink;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.entity.DeviceAlert;
import com.materin.tech.component.device.entity.DfxAlertRule;
import com.materin.tech.component.device.mapper.DeviceAlertMapper;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.materin.tech.component.device.mapper.DfxAlertRuleMapper;
import com.materin.tech.component.device.mapper.DeviceCommandLogMapper;
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
 * DFX 指标统一管道（DfxSink 实现，docs/dfx-monitoring-design.md §1.1/§5）：
 * 数值校验 → Redis 最新值 → IoTDB（dfx_ 前缀列）→ 阈值告警评估。
 * 全链路兜底异常：指标管道故障只降级，绝不阻断接入链路。
 */
@Slf4j
@Component
public class DfxIngestService implements DfxSink {

    /** 时序模型标识：与属性/事件/方法并列的第四类设备维度模型 */
    public static final String METRIC = "device_dfx";

    /** 标准数值列（列名 = dfx_ + 指标名，docs/dfx-monitoring-design.md §3） */
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

    private final DeviceMapper deviceMapper;
    private final DeviceAlertMapper alertMapper;
    private final DfxAlertRuleMapper ruleMapper;
    private final ObjectProvider<TimeSeriesManager> managerProvider;
    private final StringRedisTemplate redis;
    /** 告警抑制表：deviceId:metric -> 上次告警 epoch ms */
    private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();

    public DfxIngestService(DeviceMapper deviceMapper, DeviceAlertMapper alertMapper,
                            DfxAlertRuleMapper ruleMapper,
                            ObjectProvider<TimeSeriesManager> managerProvider,
                            StringRedisTemplate redis) {
        this.deviceMapper = deviceMapper;
        this.alertMapper = alertMapper;
        this.ruleMapper = ruleMapper;
        this.managerProvider = managerProvider;
        this.redis = redis;
    }

    // ------------------------------------------------------------ 接入

    @Override
    public void saveDfx(String deviceKey, long time, String source, Map<String, Object> metrics) {
        try {
            if (metrics == null || metrics.isEmpty()) {
                return;
            }
            Device device = requireDevice(deviceKey);
            Map<String, Object> keyed = keyByDfxPrefix(metrics);
            long ts = time <= 0 ? System.currentTimeMillis() : time;
            storeLatest(device.getDeviceKey(), source, keyed);
            saveTimeSeries(device, ts, keyed);
            evaluateRules(device, ts, metrics);
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

    private void saveTimeSeries(Device device, long time, Map<String, Object> keyed) {
        TimeSeriesManager manager = managerProvider.getIfAvailable();
        if (manager == null || device.getProductId() == null) {
            return;
        }
        TimeSeriesMetadata metadata = new TimeSeriesMetadata(
                METRIC, String.valueOf(device.getProductId()), STANDARD_COLUMNS);
        manager.getTimeSeries(metadata).save(
                new TimeSeriesData(String.valueOf(device.getId()), time, keyed));
    }

    // ------------------------------------------------------------ 阈值告警

    /** 同步评估启用中的规则；越限且不在抑制窗口则落 device_alert。 */
    private void evaluateRules(Device device, long time, Map<String, Object> metrics) {
        List<DfxAlertRule> rules = ruleMapper.selectListByQuery(
                QueryWrapper.create().eq("device_id", device.getId()).eq("enabled", 1));
        for (DfxAlertRule rule : rules) {
            Object raw = metrics.get(rule.getMetric());
            if (raw == null) {
                continue;
            }
            try {
                double value = Double.parseDouble(String.valueOf(raw));
                boolean violated = "lt".equals(rule.getComparator())
                        ? value < rule.getThreshold() : value > rule.getThreshold();
                if (violated && notSuppressed(device.getId(), rule)) {
                    raiseAlert(device, rule, value);
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

    private void raiseAlert(Device device, DfxAlertRule rule, double value) {
        DeviceAlert alert = new DeviceAlert();
        alert.setDeviceId(device.getId());
        alert.setDeviceName(device.getName());
        alert.setLevel(rule.getLevel() == null ? "warn" : rule.getLevel());
        alert.setContent(String.format("%s %.2f 超过阈值 %s %.0f（%ds 内不重复告警）",
                rule.getMetric(), value, "gt".equals(rule.getComparator()) ? ">" : "<",
                rule.getThreshold(),
                rule.getSuppressSeconds() == null ? 600 : rule.getSuppressSeconds()));
        alert.setStatus(0); // 0-未处理
        alertMapper.insert(alert);
        log.info("DFX 阈值告警: device={} {}={} {}", device.getDeviceKey(),
                rule.getMetric(), value, rule.getLevel());
    }

    // ------------------------------------------------------------ 查询

    /** DFX 历史查询（dfx_ 前缀过滤，供 REST 层调用）。 */
    public List<Map<String, Object>> queryHistory(Long deviceId, TimeSeriesQuery query) {
        Device device = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", deviceId));
        if (device == null || device.getProductId() == null) {
            return List.of();
        }
        TimeSeriesMetadata metadata = new TimeSeriesMetadata(
                METRIC, String.valueOf(device.getProductId()), STANDARD_COLUMNS);
        List<Map<String, Object>> rows = managerProvider.getIfAvailable() == null
                ? List.of()
                : managerProvider.getIfAvailable().getTimeSeries(metadata).query(
                        TimeSeriesQuery.builder(String.valueOf(deviceId))
                                .start(query.start()).end(query.end())
                                .keys(query.keys()).keyPrefix("dfx_")
                                .limit(query.limit()).desc(query.desc())
                                .aggregation(query.aggFunction(), query.aggIntervalMs())
                                .build());
        return rows;
    }

    private Device requireDevice(String deviceKey) {
        return deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("device_key", deviceKey));
    }

    /** Redis 最新值（含 source/time；time 为写入时刻的 epoch 毫秒）。 */
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
