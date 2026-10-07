package com.materin.tech.component.dfx.core.controller;

import com.materin.tech.common.core.R;
import com.materin.tech.component.dfx.core.entity.DfxDashboard;
import com.materin.tech.component.dfx.core.entity.DfxMetricConfig;
import com.materin.tech.component.dfx.core.service.DfxMetricConfigService;
import com.materin.tech.component.dfx.core.service.DfxDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** DFX 展示配置接口：指标字典 + 按产品 Dashboard（类 Grafana 配置逻辑）。 */
@Tag(name = "DFX 配置")
@RestController
@RequestMapping("/device/dfx")
@RequiredArgsConstructor
public class DfxConfigController {

    private final DfxMetricConfigService metricService;
    private final DfxDashboardService dashboardService;

    @Operation(summary = "指标字典列表（上报 key -> 汉字表述）")
    @GetMapping("/metric/list")
    public R<List<DfxMetricConfig>> metrics() {
        return R.ok(metricService.listAll());
    }

    @Operation(summary = "新增/更新指标定义（自定义指标即配置即纳管）")
    @PostMapping("/metric")
    public R<DfxMetricConfig> metricSave(@RequestBody DfxMetricConfig config) {
        return R.ok(metricService.save(config));
    }

    @Operation(summary = "删除指标定义")
    @DeleteMapping("/metric/{id}")
    public R<Void> metricDelete(@PathVariable Long id) {
        metricService.delete(id);
        return R.ok(null);
    }

    @Operation(summary = "按产品 Dashboard 列表")
    @GetMapping("/dashboard/list")
    public R<List<DfxDashboard>> dashboards(@RequestParam Long productId) {
        return R.ok(dashboardService.listByProduct(productId));
    }

    @Operation(summary = "保存产品 Dashboard（整份配置覆盖保存）")
    @PostMapping("/dashboard")
    public R<DfxDashboard> dashboardSave(@RequestBody DfxDashboard dashboard) {
        return R.ok(dashboardService.save(dashboard));
    }

    @Operation(summary = "删除产品 Dashboard")
    @DeleteMapping("/dashboard/{id}")
    public R<Void> dashboardDelete(@PathVariable Long id) {
        dashboardService.delete(id);
        return R.ok(null);
    }
}
