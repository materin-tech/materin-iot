package com.materin.tech.component.device.controller;

import com.materin.tech.common.core.R;
import com.materin.tech.component.device.timeseries.DeviceTimeSeriesBridge;
import com.materin.tech.component.timeseries.TimeSeriesQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 设备时序历史查询：属性/事件/方法同一设备维度，keyPrefix 区分。 */
@Tag(name = "设备时序数据")
@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceTelemetryController {

    private static final String EVENT_PREFIX = "evt_";
    private static final String METHOD_PREFIX = "cmd_";
    private static final long DEFAULT_AGG_INTERVAL_MS = 60_000L;

    private final DeviceTimeSeriesBridge bridge;

    @Operation(summary = "属性历史查询",
            description = "时间范围 [start,end]（epoch 毫秒）；agg 可选 avg/sum/max/min/count 等，需配 interval")
    @GetMapping("/{id}/telemetry/history")
    public R<List<Map<String, Object>>> telemetryHistory(
            @PathVariable Long id,
            @Parameter(description = "起始时间（epoch 毫秒）") @RequestParam(required = false) Long start,
            @Parameter(description = "结束时间（epoch 毫秒）") @RequestParam(required = false) Long end,
            @Parameter(description = "列名（逗号分隔，空 = 全部）") @RequestParam(required = false) String keys,
            @Parameter(description = "聚合函数") @RequestParam(required = false) String agg,
            @Parameter(description = "聚合窗口（毫秒，默认 60000）") @RequestParam(required = false) Long interval,
            @Parameter(description = "条数上限（默认 1000）") @RequestParam(required = false) Integer limit,
            @Parameter(description = "是否倒序") @RequestParam(required = false, defaultValue = "false") boolean desc) {
        return R.ok(bridge.queryHistory(id, DeviceTimeSeriesBridge.MetricKind.PROPERTY, buildQuery(id,
                start, end, keys, null, agg, interval, limit, desc)));
    }

    @Operation(summary = "事件历史查询", description = "按 evt_ 前缀过滤；keys 指定具体 evt_ 列")
    @GetMapping("/{id}/events/history")
    public R<List<Map<String, Object>>> eventHistory(
            @PathVariable Long id,
            @RequestParam(required = false) Long start,
            @RequestParam(required = false) Long end,
            @RequestParam(required = false) String keys,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "true") boolean desc) {
        return R.ok(bridge.queryHistory(id, DeviceTimeSeriesBridge.MetricKind.EVENT,
                buildQuery(id, start, end, keys, EVENT_PREFIX, null, null, limit, desc)));
    }

    @Operation(summary = "方法调用历史查询", description = "按 cmd_ 前缀过滤；keys 指定具体 cmd_ 列")
    @GetMapping("/{id}/methods/history")
    public R<List<Map<String, Object>>> methodHistory(
            @PathVariable Long id,
            @RequestParam(required = false) Long start,
            @RequestParam(required = false) Long end,
            @RequestParam(required = false) String keys,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "true") boolean desc) {
        return R.ok(bridge.queryHistory(id, DeviceTimeSeriesBridge.MetricKind.METHOD,
                buildQuery(id, start, end, keys, METHOD_PREFIX, null, null, limit, desc)));
    }

    private TimeSeriesQuery buildQuery(Long id, Long start, Long end, String keys, String keyPrefix,
                                       String agg, Long interval, Integer limit, boolean desc) {
        return TimeSeriesQuery.builder(String.valueOf(id))
                .start(start).end(end)
                .keys(splitKeys(keys)).keyPrefix(keyPrefix)
                .limit(limit).desc(desc)
                .aggregation(agg, interval == null ? DEFAULT_AGG_INTERVAL_MS : interval)
                .build();
    }

    private List<String> splitKeys(String keys) {
        if (keys == null || keys.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keys.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .toList();
    }
}
