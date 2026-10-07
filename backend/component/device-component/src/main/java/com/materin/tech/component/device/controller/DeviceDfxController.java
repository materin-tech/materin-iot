package com.materin.tech.component.device.controller;

import com.materin.tech.common.core.R;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.device.dfx.DfxIngestService;
import com.materin.tech.component.device.entity.DfxAlertRule;
import com.materin.tech.component.device.entity.DfxSnmpTarget;
import com.materin.tech.component.device.service.DfxAlertRuleService;
import com.materin.tech.component.device.service.DfxSnmpTargetService;
import com.materin.tech.component.timeseries.TimeSeriesQuery;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 设备 DFX 接口（docs/dfx-monitoring-design.md §4.2/§7）：
 * - HTTP 接入标准端点（设备 secret 鉴权，单设备/网关代理批量）
 * - 最新值 / 历史查询 / SNMP 目标与阈值规则 CRUD
 */
@Tag(name = "设备运维 DFX")
@RestController
@RequestMapping("/device/dfx")
@RequiredArgsConstructor
public class DeviceDfxController {

    private final DfxIngestService ingest;
    private final DfxAlertRuleService ruleService;
    private final DfxSnmpTargetService targetService;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;

    // ---------------- HTTP 接入标准（§4.2） ----------------

    @Operation(summary = "DFX 指标上报（HTTP 接入标准，设备 secret 鉴权）")
    @PostMapping("/report")
    public R<Map<String, Object>> report(@RequestBody Map<String, Object> body) {
        String deviceKey = str(body.get("deviceKey"));
        String secret = str(body.get("secret"));
        if (deviceKey == null || secret == null) {
            throw new com.materin.tech.common.exception.BizException(400, "deviceKey/secret 不能为空");
        }
        if (!authenticate(deviceKey, secret)) {
            throw new com.materin.tech.common.exception.BizException(401, "设备凭证校验失败");
        }
        long time = body.get("time") instanceof Number n ? n.longValue() : 0L;
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) body.get("metrics");
        ingest.saveDfx(deviceKey, time, "http", metrics == null ? Map.of() : metrics);
        return R.ok(Map.of("accepted", metrics == null ? 0 : metrics.size()));
    }

    @Operation(summary = "DFX 指标批量上报（网关代理，gw 凭证鉴权）")
    @PostMapping("/report/batch")
    public R<Map<String, Object>> reportBatch(@RequestBody Map<String, Object> body) {
        String deviceKey = str(body.get("deviceKey"));
        String secret = str(body.get("secret"));
        if (deviceKey == null || secret == null || !authenticate(deviceKey, secret)) {
            throw new com.materin.tech.common.exception.BizException(401, "设备凭证校验失败");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devices = (List<Map<String, Object>>) body.get("devices");
        if (devices == null || devices.isEmpty()) {
            throw new com.materin.tech.common.exception.BizException(400, "devices 不能为空");
        }
        int accepted = 0;
        for (Map<String, Object> item : devices) {
            String dk = str(item.get("deviceKey"));
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) item.get("metrics");
            if (dk == null || metrics == null || metrics.isEmpty()) {
                continue;
            }
            long time = item.get("time") instanceof Number n ? n.longValue() : 0L;
            ingest.saveDfx(dk, time, "http", metrics);
            accepted++;
        }
        return R.ok(Map.of("accepted", accepted));
    }

    // ---------------- 查询 ----------------

    @Operation(summary = "DFX 最新值")
    @GetMapping("/latest")
    public R<Map<String, Object>> latest(@RequestParam String deviceKey) {
        return R.ok(ingest.latestOf(deviceKey));
    }

    @Operation(summary = "DFX 历史查询（支持聚合）")
    @GetMapping("/history")
    public R<List<Map<String, Object>>> history(@RequestParam Long deviceId,
                                                @RequestParam(required = false) Long start,
                                                @RequestParam(required = false) Long end,
                                                @RequestParam(required = false) String keys,
                                                @RequestParam(required = false) String agg,
                                                @RequestParam(required = false) Long interval,
                                                @RequestParam(defaultValue = "1000") int limit) {
        TimeSeriesQuery q = TimeSeriesQuery.builder(String.valueOf(deviceId))
                .start(start).end(end).keys(keys == null ? null : List.of(keys.split(",")))
                .limit(limit).build();
        if (agg != null && !agg.isBlank()) {
            q = TimeSeriesQuery.builder(String.valueOf(deviceId))
                    .start(start).end(end).keys(keys == null ? null : List.of(keys.split(",")))
                    .limit(limit).aggregation(agg, interval == null ? 60_000L : interval)
                    .build();
        }
        return R.ok(ingest.queryHistory(deviceId, q));
    }

    // ---------------- SNMP 目标 CRUD ----------------

    @Operation(summary = "SNMP 轮询目标列表")
    @GetMapping("/target/list")
    public R<Page<DfxSnmpTarget>> targetList(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long deviceId) {
        return R.ok(targetService.list(page, pageSize, deviceId));
    }

    @Operation(summary = "创建 SNMP 轮询目标")
    @PostMapping("/target")
    public R<DfxSnmpTarget> targetCreate(@RequestBody DfxSnmpTarget target) {
        return R.ok(targetService.create(target));
    }

    @Operation(summary = "更新 SNMP 轮询目标")
    @PutMapping("/target/{id}")
    public R<DfxSnmpTarget> targetUpdate(@PathVariable Long id, @RequestBody DfxSnmpTarget patch) {
        return R.ok(targetService.update(id, patch));
    }

    @Operation(summary = "删除 SNMP 轮询目标")
    @DeleteMapping("/target/{id}")
    public R<Void> targetDelete(@PathVariable Long id) {
        targetService.delete(id);
        return R.ok(null);
    }

    // ---------------- 阈值规则 CRUD ----------------

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

    // ---------------- 内部 ----------------

    private boolean authenticate(String deviceKey, String secret) {
        DeviceCredentialLookup lookup = credentialLookup.getIfAvailable();
        if (lookup == null) {
            return false;
        }
        return lookup.findByKey(deviceKey)
                .map(c -> c.secret() != null && c.secret().equals(secret))
                .orElse(false);
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}