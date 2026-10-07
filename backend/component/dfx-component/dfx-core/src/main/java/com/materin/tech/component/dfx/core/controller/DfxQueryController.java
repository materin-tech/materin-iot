package com.materin.tech.component.dfx.core.controller;

import com.materin.tech.common.core.R;
import com.materin.tech.component.dfx.core.DfxIngestService;
import com.materin.tech.component.dfx.core.entity.DfxAlertRule;
import com.materin.tech.component.dfx.core.health.DfxAccessHealth;
import com.materin.tech.component.dfx.core.health.DfxAccessHealth;
import com.materin.tech.component.dfx.core.health.DfxAccessHealthProvider;
import com.materin.tech.component.dfx.core.health.DfxHealthRegistry;
import com.materin.tech.component.dfx.core.service.DfxAlertRuleService;
import com.materin.tech.component.dfx.core.DfxIngestService;
import com.materin.tech.component.dfx.core.DfxRecord;
import com.materin.tech.component.timeseries.TimeSeriesQuery;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DFX 通用接口（dfx-core，协议无关）：
 * 最新值 / 历史（聚合）/ 阈值规则 CRUD / 接入组件健康状态。
 */
@Tag(name = "设备运维 DFX")
@RestController
@RequestMapping("/device/dfx")
@RequiredArgsConstructor
public class DfxQueryController {

    private final DfxIngestService ingest;
    private final DfxAlertRuleService ruleService;
    private final ObjectProvider<DfxAccessHealthProvider> healthProviders;
    private final DfxHealthRegistry healthRegistry;

    @Operation(summary = "DFX 最新值")
    @GetMapping("/latest")
    public R<Map<String, Object>> latest(@RequestParam String deviceKey) {
        return R.ok(ingest.latestOf(deviceKey));
    }

    @Operation(summary = "DFX 历史查询（支持 agg/interval 聚合）")
    @GetMapping("/history")
    public R<List<Map<String, Object>>> history(@RequestParam Long deviceId,
                                                @RequestParam(required = false) Long start,
                                                @RequestParam(required = false) Long end,
                                                @RequestParam(required = false) String keys,
                                                @RequestParam(required = false) String agg,
                                                @RequestParam(required = false) Long interval,
                                                @RequestParam(defaultValue = "1000") int limit) {
        List<String> keyList = keys == null ? null : List.of(keys.split(","));
        TimeSeriesQuery.Builder b = TimeSeriesQuery.builder(String.valueOf(deviceId))
                .start(start).end(end).keys(keyList)
                .limit(limit);
        if (agg != null && !agg.isBlank()) {
            b.aggregation(agg, interval == null ? 60_000L : interval);
        }
        return R.ok(ingest.queryHistory(deviceId, b.build()));
    }

    @Operation(summary = "DFX 阈值规则列表")
    @GetMapping("/rule/list")
    public R<Page<DfxAlertRule>> ruleList(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long deviceId) {
        return R.ok(ruleService.list(page, pageSize, deviceId));
    }

    @Operation(summary = "创建阈值规则")
    @PostMapping("/rule")
    public R<DfxAlertRule> ruleCreate(@RequestBody DfxAlertRule rule) {
        return R.ok(ruleService.create(rule));
    }

    @Operation(summary = "更新阈值规则")
    @PutMapping("/rule/{id}")
    public R<DfxAlertRule> ruleUpdate(@PathVariable Long id, @RequestBody DfxAlertRule patch) {
        return R.ok(ruleService.update(id, patch));
    }

    @Operation(summary = "删除阈值规则")
    @DeleteMapping("/rule/{id}")
    public R<Void> ruleDelete(@PathVariable Long id) {
        ruleService.delete(id);
        return R.ok(null);
    }

    @Operation(summary = "接入组件健康状态（各协议子模块 + 管道统计）")
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        List<DfxAccessHealth> components = new ArrayList<>();
        for (DfxAccessHealthProvider p : healthProviders) {
            try {
                components.add(p.health());
            } catch (Exception ignore) {
                // 健康探针异常不阻塞接口
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("components", components);
        result.put("pipeline", healthRegistry.sourceStats());
        return R.ok(result);
    }
}
