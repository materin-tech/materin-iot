package com.materin.tech.component.device.timeseries;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.spi.TelemetrySink;
import com.materin.tech.common.spi.ThingModelLookup;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.materin.tech.component.timeseries.TimeSeriesData;
import com.materin.tech.component.timeseries.TimeSeriesManager;
import com.materin.tech.component.timeseries.TimeSeriesMetadata;
import com.materin.tech.component.timeseries.TimeSeriesQuery;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备域 → 时序域桥接（TelemetrySink 实现）：
 * 按 deviceKey 反查设备与产品 → 物模型解析列定义（带 TTL 缓存）→ TimeSeriesManager 落库。
 * 全链路兜底异常：时序库不可用只降级告警，绝不阻断上行/指令链路。
 */
@Slf4j
@Component
public class DeviceTimeSeriesBridge implements TelemetrySink {

    /** 设备维度时序的三类模型（同一设备维度、同一套列逻辑） */
    public enum MetricKind { PROPERTY, EVENT, METHOD }

    private static final long SCHEMA_TTL_MS = 60_000L;

    private final DeviceMapper deviceMapper;
    private final ObjectProvider<TimeSeriesManager> managerProvider;
    private final ObjectProvider<ThingModelLookup> thingModelLookup;
    private final ObjectMapper objectMapper;
    private final Map<Long, CachedSchema> schemaCache = new ConcurrentHashMap<>();

    public DeviceTimeSeriesBridge(DeviceMapper deviceMapper,
                                  ObjectProvider<TimeSeriesManager> managerProvider,
                                  ObjectProvider<ThingModelLookup> thingModelLookup,
                                  ObjectMapper objectMapper) {
        this.deviceMapper = deviceMapper;
        this.managerProvider = managerProvider;
        this.thingModelLookup = thingModelLookup;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------ 上行落库

    @Override
    public void saveTelemetry(String deviceKey, long time, Map<String, Object> values) {
        saveQuietly(deviceKey, time, values, MetricKind.PROPERTY, "遥测");
    }

    @Override
    public void saveEvent(String deviceKey, long time, Map<String, Object> payload) {
        try {
            Device device = requireDevice(deviceKey);
            String eventId = firstText(payload, "id", "identifier", "name");
            Map<String, Object> data = extractEventData(payload);
            Map<String, Object> keyed = new HashMap<>();
            data.forEach((k, v) -> keyed.put("evt_" + eventId + "_" + k, v));
            if (keyed.isEmpty()) {
                keyed.put("evt_" + eventId + "_data", toJson(payload));
            }
            saveInternal(device, time, keyed, MetricKind.EVENT);
        } catch (Exception e) {
            log.warn("事件落时序库失败（忽略）: deviceKey={}, {}", deviceKey, e.getMessage());
        }
    }

    @Override
    public void saveMethodRecord(Long deviceId, String methodId, String requestJson,
                                 String responseJson, boolean success, long costMs) {
        try {
            Device device = requireDeviceById(deviceId);
            String mid = methodId == null || methodId.isBlank() ? "unknown" : methodId;
            Map<String, Object> keyed = Map.of(
                    "cmd_" + mid + "_req", nullToEmpty(requestJson),
                    "cmd_" + mid + "_resp", nullToEmpty(responseJson),
                    "cmd_" + mid + "_success", success,
                    "cmd_" + mid + "_cost_ms", costMs);
            saveInternal(device, System.currentTimeMillis(), keyed, MetricKind.METHOD);
        } catch (Exception e) {
            log.warn("方法调用记录落时序库失败（忽略）: deviceId={}, {}", deviceId, e.getMessage());
        }
    }

    // ------------------------------------------------------------ 历史查询

    /** 设备维度历史查询（属性/事件/方法同一入口，keyPrefix 区分）。 */
    public List<Map<String, Object>> queryHistory(Long deviceId, MetricKind kind, TimeSeriesQuery query) {
        TimeSeriesManager manager = managerProvider.getIfAvailable();
        Device device = deviceMapper.selectOneByQuery(QueryWrapper.create().eq("id", deviceId));
        if (manager == null || device == null || device.getProductId() == null) {
            return List.of();
        }
        TimeSeriesMetadata metadata = schemaOf(device.getProductId()).metadataOf(kind);
        List<Map<String, Object>> rows = manager.getTimeSeries(metadata).query(
                TimeSeriesQuery.builder(String.valueOf(deviceId))
                        .start(query.start()).end(query.end())
                        .keys(query.keys()).keyPrefix(query.keyPrefix())
                        .limit(query.limit()).desc(query.desc())
                        .aggregation(query.aggFunction(), query.aggIntervalMs())
                        .build());
        return filterColumnsByKind(rows, kind);
    }

    /**
     * 按指标类型过滤结果列（time 保留）：属性列无统一前缀，SELECT * 会混入
     * evt_/cmd_ 列，故在结果侧剔除，保证属性/事件/方法三个接口语义纯净。
     */
    private List<Map<String, Object>> filterColumnsByKind(List<Map<String, Object>> rows, MetricKind kind) {
        List<String> excludedPrefixes = switch (kind) {
            case PROPERTY -> List.of("evt_", "cmd_");
            case EVENT -> List.of("cmd_");
            case METHOD -> List.of("evt_");
        };
        List<Map<String, Object>> filtered = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> clean = new LinkedHashMap<>(row.size());
            row.forEach((k, v) -> {
                if ("time".equals(k) || excludedPrefixes.stream().noneMatch(k::startsWith)) {
                    clean.put(k, v);
                }
            });
            filtered.add(clean);
        }
        return filtered;
    }

    // ------------------------------------------------------------ 内部

    private void saveQuietly(String deviceKey, long time, Map<String, Object> values,
                             MetricKind kind, String label) {
        try {
            if (values == null || values.isEmpty()) {
                return;
            }
            Device device = requireDevice(deviceKey);
            saveInternal(device, time, values, kind);
        } catch (Exception e) {
            log.warn("{}落时序库失败（忽略）: deviceKey={}, {}", label, deviceKey, e.getMessage());
        }
    }

    private void saveInternal(Device device, long time, Map<String, Object> keyed, MetricKind kind) {
        TimeSeriesManager manager = managerProvider.getIfAvailable();
        if (manager == null || device.getProductId() == null) {
            return;
        }
        TimeSeriesMetadata metadata = schemaOf(device.getProductId()).metadataOf(kind);
        manager.getTimeSeries(metadata).save(
                new TimeSeriesData(String.valueOf(device.getId()), time, keyed));
    }

    /** 事件报文宽容解析：{id, data:{...}} / {id, ...散参} / 整包。 */
    private Map<String, Object> extractEventData(Map<String, Object> payload) {
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        Map<String, Object> rest = new LinkedHashMap<>(payload);
        List.of("id", "identifier", "name").forEach(rest::remove);
        return rest;
    }

    private Device requireDevice(String deviceKey) {
        Device device = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("device_key", deviceKey));
        if (device == null) {
            throw new IllegalStateException("设备不存在: " + deviceKey);
        }
        return device;
    }

    private Device requireDeviceById(Long deviceId) {
        Device device = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", deviceId));
        if (device == null) {
            throw new IllegalStateException("设备不存在: " + deviceId);
        }
        return device;
    }

    /** 产品模式缓存（TTL 60s，物模型发布后最长 1 分钟生效）。 */
    private CachedSchema schemaOf(Long productId) {
        long now = System.currentTimeMillis();
        CachedSchema cached = schemaCache.get(productId);
        if (cached != null && now - cached.at() < SCHEMA_TTL_MS) {
            return cached;
        }
        synchronized (schemaCache) {
            cached = schemaCache.get(productId);
            if (cached == null || now - cached.at() >= SCHEMA_TTL_MS) {
                ThingModelLookup lookup = thingModelLookup.getIfAvailable();
                Optional<ThingModelLookup.ThingModelDigest> digest = lookup == null
                        ? Optional.empty() : lookup.findPublishedByProductId(productId);
                ProductSchema schema = digest
                        .map(m -> ProductSchema.parse(productId, m))
                        .orElseGet(() -> ProductSchema.empty(productId));
                cached = new CachedSchema(schema, now);
                schemaCache.put(productId, cached);
            }
            return cached;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String firstText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "unknown";
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record CachedSchema(ProductSchema schema, long at) {
        TimeSeriesMetadata metadataOf(MetricKind kind) {
            return switch (kind) {
                case PROPERTY -> schema.property();
                case EVENT -> schema.event();
                case METHOD -> schema.method();
            };
        }
    }
}
