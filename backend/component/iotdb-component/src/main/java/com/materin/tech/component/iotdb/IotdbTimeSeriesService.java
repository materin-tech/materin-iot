package com.materin.tech.component.iotdb;

import com.materin.tech.component.timeseries.TimeSeriesColumn;
import com.materin.tech.component.timeseries.TimeSeriesData;
import com.materin.tech.component.timeseries.TimeSeriesMetadata;
import com.materin.tech.component.timeseries.TimeSeriesQuery;
import com.materin.tech.component.timeseries.TimeSeriesService;
import com.materin.tech.component.timeseries.ValueType;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.read.common.Field;
import org.apache.tsfile.read.common.RowRecord;
import org.apache.iotdb.isession.pool.SessionDataSetWrapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IoTDB 单模型时序服务：
 * - 写入：按元数据列类型转换，列不存在时懒建时序（设备维度：{db}.p{modelId}.d{id}）；
 *   未建模字段以 TEXT 兜底落库（String.valueOf），保证上行数据不丢。
 * - 查询：树模型 SQL，keyPrefix 通过 SHOW TIMESERIES 展开为实际测量列。
 */
@Slf4j
public class IotdbTimeSeriesService implements TimeSeriesService {

    private static final Map<ValueType, TSDataType> TYPE_MAPPING = Map.of(
            ValueType.LONG, TSDataType.INT64,
            ValueType.DOUBLE, TSDataType.DOUBLE,
            ValueType.BOOLEAN, TSDataType.BOOLEAN,
            ValueType.TEXT, TSDataType.TEXT);

    private static final Map<ValueType, String> ENCODING_MAPPING = Map.of(
            ValueType.LONG, "TS_2DIFF",
            ValueType.DOUBLE, "GORILLA",
            ValueType.BOOLEAN, "RLE",
            ValueType.TEXT, "PLAIN");

    private final SessionPool sessionPool;
    private final String modelPathPrefix;
    private final Map<String, TimeSeriesColumn> columns;
    private final Runnable databaseEnsurer;
    /** 已确认存在的时序路径，避免重复 DDL */
    private final Set<String> createdSeries = ConcurrentHashMap.newKeySet();
    /** keyPrefix 展开结果缓存：prefix → 测量短名列表 */
    private final Map<String, List<String>> prefixColumns = new ConcurrentHashMap<>();

    public IotdbTimeSeriesService(SessionPool sessionPool, String database,
                                  TimeSeriesMetadata metadata, Runnable databaseEnsurer) {
        this.sessionPool = sessionPool;
        this.modelPathPrefix = database + "." + IotdbPathBuilder.modelNode(metadata.modelId());
        this.columns = metadata.columnMap();
        this.databaseEnsurer = databaseEnsurer;
    }

    @Override
    public void save(TimeSeriesData data) {
        if (data.data().isEmpty()) {
            return;
        }
        databaseEnsurer.run();
        String devicePath = modelPathPrefix + "." + IotdbPathBuilder.deviceNode(data.id());

        List<String> measurements = new ArrayList<>(data.data().size());
        List<TSDataType> types = new ArrayList<>(data.data().size());
        List<Object> values = new ArrayList<>(data.data().size());
        data.data().forEach((key, value) -> {
            if (value == null) {
                return;
            }
            String measurement = IotdbPathBuilder.safe(key);
            ValueType type = columnType(measurement, key);
            Object converted = convert(value, type);
            if (converted == null) {
                return;
            }
            measurements.add(measurement);
            types.add(TYPE_MAPPING.get(type));
            values.add(converted);
        });
        if (measurements.isEmpty()) {
            return;
        }
        ensureSeries(devicePath, measurements, types);
        try {
            sessionPool.insertRecord(devicePath, data.time(), measurements, types, values);
        } catch (Exception e) {
            throw new IllegalStateException("IoTDB 写入失败: " + devicePath + " - " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> query(TimeSeriesQuery query) {
        String devicePath = modelPathPrefix + "." + IotdbPathBuilder.deviceNode(query.id());
        List<String> resolvedKeys = query.keys().isEmpty() && hasText(query.keyPrefix())
                ? expandPrefix(devicePath, query.keyPrefix())
                : query.keys();
        TimeSeriesQuery effective = TimeSeriesQuery.builder(query.id())
                .start(query.start()).end(query.end())
                .keys(resolvedKeys).limit(query.limit()).desc(query.desc())
                .aggregation(query.aggFunction(), query.aggIntervalMs())
                .build();

        String sql = effective.aggFunction() != null
                ? IotdbQueryBuilder.aggregationQuery(devicePath, effective)
                : IotdbQueryBuilder.rawQuery(devicePath, effective);
        try (SessionDataSetWrapper dataSet = sessionPool.executeQueryStatement(sql)) {
            return mapRows(devicePath, dataSet);
        } catch (Exception e) {
            throw new IllegalStateException("IoTDB 查询失败: " + sql + " - " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------ 写入支撑

    private ValueType columnType(String measurement, String rawKey) {
        TimeSeriesColumn column = columns.get(measurement);
        if (column != null) {
            return column.type();
        }
        return columns.containsKey(rawKey)
                ? columns.get(rawKey).type()
                : ValueType.TEXT;
    }

    /** 值按列类型转换；无法转换返回 null（跳过该字段，不失败整行）。 */
    private Object convert(Object value, ValueType type) {
        try {
            return switch (type) {
                case LONG -> toLong(value);
                case DOUBLE -> toDouble(value);
                case BOOLEAN -> toBoolean(value);
                case TEXT -> value instanceof String s ? s : String.valueOf(value);
            };
        } catch (Exception e) {
            log.debug("时序值类型转换失败，跳过: value={} type={}", value, type);
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof Boolean b) {
            return b ? 1L : 0L;
        }
        return Long.parseLong(String.valueOf(value).trim());
    }

    private Double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value).trim());
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("非法布尔值: " + value);
    }

    /** 懒建时序：内存缓存 + already-exists 容错（2.0 无 IF NOT EXISTS）。 */
    private void ensureSeries(String devicePath, List<String> measurements, List<TSDataType> types) {
        for (int i = 0; i < measurements.size(); i++) {
            String fullPath = devicePath + "." + measurements.get(i);
            if (!createdSeries.add(fullPath)) {
                continue;
            }
            ValueType valueType = valueTypeOf(types.get(i));
            String sql = "CREATE TIMESERIES " + fullPath
                    + " WITH DATATYPE=" + types.get(i)
                    + ", ENCODING=" + ENCODING_MAPPING.get(valueType);
            try {
                sessionPool.executeNonQueryStatement(sql);
            } catch (Exception e) {
                if (!IotdbTimeSeriesManager.isAlreadyExists(e)) {
                    // 建失败（如类型冲突）不允许缓存假象，移除标记允许后续重试
                    createdSeries.remove(fullPath);
                    log.warn("创建时序失败: {} - {}", fullPath, e.getMessage());
                }
            }
        }
    }

    private ValueType valueTypeOf(TSDataType dataType) {
        return switch (dataType) {
            case INT32, INT64, DATE -> ValueType.LONG;
            case FLOAT, DOUBLE -> ValueType.DOUBLE;
            case BOOLEAN -> ValueType.BOOLEAN;
            default -> ValueType.TEXT;
        };
    }

    // ------------------------------------------------------------ 查询支撑

    /** SHOW TIMESERIES 展开 keyPrefix 为实际测量短名（结果缓存）。 */
    private List<String> expandPrefix(String devicePath, String keyPrefix) {
        String prefix = IotdbPathBuilder.safe(keyPrefix);
        return prefixColumns.computeIfAbsent(prefix, p -> {
            List<String> names = new ArrayList<>();
            try (SessionDataSetWrapper dataSet =
                         sessionPool.executeQueryStatement("SHOW TIMESERIES " + devicePath + "." + p + "*")) {
                while (dataSet.hasNext()) {
                    RowRecord row = dataSet.next();
                    String fullPath = row.getFields().get(0).getStringValue();
                    if (fullPath.startsWith(devicePath + ".")) {
                        names.add(fullPath.substring(devicePath.length() + 1));
                    }
                }
            } catch (Exception e) {
                log.warn("展开时序前缀失败: {}.{}, {}", devicePath, p, e.getMessage());
            }
            return List.copyOf(names);
        });
    }

    private List<Map<String, Object>> mapRows(String devicePath, SessionDataSetWrapper dataSet)
            throws IoTDBConnectionException, StatementExecutionException {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columnNames = dataSet.getColumnNames();
        while (dataSet.hasNext()) {
            RowRecord record = dataSet.next();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", record.getTimestamp());
            List<Field> fields = record.getFields();
            for (int i = 0; i < fields.size() && i < columnNames.size() - 1; i++) {
                Field field = fields.get(i);
                if (field == null || field.getDataType() == null) {
                    continue;
                }
                row.put(shortName(devicePath, columnNames.get(i + 1)), fieldValue(field));
            }
            rows.add(row);
        }
        return rows;
    }

    /** 列名短化：去掉设备路径前缀；聚合列 avg(root...m) → avg(m)。 */
    static String shortName(String devicePath, String columnName) {
        if (columnName.startsWith(devicePath + ".")) {
            return columnName.substring(devicePath.length() + 1);
        }
        int open = columnName.indexOf('(');
        int close = columnName.lastIndexOf(')');
        if (open >= 0 && close > open) {
            String fn = columnName.substring(0, open + 1);
            String inner = columnName.substring(open + 1, close);
            int dot = inner.lastIndexOf('.');
            return fn + (dot >= 0 ? inner.substring(dot + 1) : inner) + ")";
        }
        return columnName;
    }

    private Object fieldValue(Field field) {
        return switch (field.getDataType()) {
            case BOOLEAN -> field.getBoolV();
            case INT32 -> field.getIntV();
            case INT64 -> field.getLongV();
            case FLOAT -> field.getFloatV();
            case DOUBLE -> field.getDoubleV();
            case TEXT, STRING -> field.getBinaryV() == null ? null
                    : new String(field.getBinaryV().getValues(), StandardCharsets.UTF_8);
            default -> field.getObjectValue(field.getDataType());
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
