package com.materin.tech.component.iotdb;

import com.materin.tech.component.timeseries.TimeSeriesQuery;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IoTDB 2.0 树模型查询 SQL 构造。
 * 列名统一经 {@link IotdbPathBuilder#safe} 安全化后再拼接，聚合函数白名单校验，杜绝注入。
 */
public final class IotdbQueryBuilder {

    /** 支持的聚合函数（IoTDB 内置时间序列聚合）。 */
    private static final Set<String> AGG_FUNCTIONS = Set.of(
            "avg", "sum", "max", "min", "count", "first_value", "last_value");

    private static final int MAX_LIMIT = 10000;
    private static final int DEFAULT_LIMIT = 1000;
    /** 聚合窗口数上限：结果行数 ≤ 窗口数，防止超宽时间范围 × 小窗口把堆打爆 */
    private static final long MAX_AGG_WINDOWS = 10_000L;

    private IotdbQueryBuilder() {
    }

    /** 原始数据查询：SELECT cols FROM device WHERE time ... ORDER BY/LIMIT。 */
    public static String rawQuery(String devicePath, TimeSeriesQuery query) {
        StringBuilder sql = new StringBuilder("SELECT ").append(selectColumns(query))
                .append(" FROM ").append(devicePath);
        appendTimeRange(sql, query);
        if (query.desc()) {
            sql.append(" ORDER BY time DESC");
        }
        sql.append(" LIMIT ").append(normalizeLimit(query.limit()));
        return sql.toString();
    }

    /** 聚合查询：SELECT fn(col)... FROM device WHERE time ... GROUP BY([start, end), interval)。 */
    public static String aggregationQuery(String devicePath, TimeSeriesQuery query) {
        String fn = normalizeAggFunction(query.aggFunction());
        List<String> columns = resolveColumns(query);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("聚合查询必须指定列（keys）");
        }
        long intervalMs = query.aggIntervalMs() == null || query.aggIntervalMs() <= 0
                ? 60_000L : query.aggIntervalMs();
        String select = columns.stream()
                .map(c -> fn + "(" + c + ")")
                .collect(Collectors.joining(", "));
        StringBuilder sql = new StringBuilder("SELECT ").append(select)
                .append(" FROM ").append(devicePath);
        appendTimeRange(sql, query);
        long end = query.end() == null ? System.currentTimeMillis() : query.end();
        // 未指定 start 时只回看 MAX_AGG_WINDOWS 个窗口，避免从 epoch 起物化百万级空窗口
        long start = query.start() == null
                ? Math.max(0L, end - intervalMs * MAX_AGG_WINDOWS) : query.start();
        if (end - start > intervalMs * MAX_AGG_WINDOWS) {
            throw new IllegalArgumentException(
                    "聚合窗口数超过上限 " + MAX_AGG_WINDOWS + "，请收窄时间范围或增大 interval");
        }
        sql.append(" GROUP BY([").append(start).append(", ").append(end).append("), ")
                .append(durationLiteral(intervalMs)).append(")");
        return sql.toString();
    }

    /** 列清单：keys 优先，其次 keyPrefix 交由调用方展开（返回空表示 SELECT *）。 */
    public static List<String> resolveColumns(TimeSeriesQuery query) {
        if (!query.keys().isEmpty()) {
            return query.keys().stream().map(IotdbPathBuilder::safe).toList();
        }
        if (query.keyPrefix() != null && !query.keyPrefix().isBlank()) {
            return List.of(IotdbPathBuilder.safe(query.keyPrefix()) + "*");
        }
        return List.of();
    }

    private static String selectColumns(TimeSeriesQuery query) {
        List<String> columns = resolveColumns(query);
        if (columns.isEmpty()) {
            return "*";
        }
        return String.join(", ", columns);
    }

    private static void appendTimeRange(StringBuilder sql, TimeSeriesQuery query) {
        if (query.start() != null) {
            sql.append(" WHERE time >= ").append(query.start());
        }
        if (query.end() != null) {
            sql.append(query.start() == null ? " WHERE" : " AND")
                    .append(" time <= ").append(query.end());
        }
    }

    private static String normalizeAggFunction(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("聚合函数不能为空");
        }
        String fn = raw.trim().toLowerCase();
        if (!AGG_FUNCTIONS.contains(fn)) {
            throw new IllegalArgumentException("不支持的聚合函数: " + raw);
        }
        return fn;
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /** 毫秒 → IoTDB 时长字面量（优先大单位：d/h/m/s，不足 1s 用 ms）。 */
    static String durationLiteral(long intervalMs) {
        if (intervalMs % 86_400_000L == 0) {
            return (intervalMs / 86_400_000L) + "d";
        }
        if (intervalMs % 3_600_000L == 0) {
            return (intervalMs / 3_600_000L) + "h";
        }
        if (intervalMs % 60_000L == 0) {
            return (intervalMs / 60_000L) + "m";
        }
        if (intervalMs % 1_000L == 0) {
            return (intervalMs / 1_000L) + "s";
        }
        return intervalMs + "ms";
    }
}
