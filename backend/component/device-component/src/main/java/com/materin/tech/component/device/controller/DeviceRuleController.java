package com.materin.tech.component.device.controller;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.core.R;
import com.materin.tech.component.device.entity.DeviceRule;
import com.materin.tech.component.device.service.DeviceRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 设备规则接口：/api/v1/device/rule。 */
@Tag(name = "设备规则")
@RestController
@RequestMapping("/device/rule")
@RequiredArgsConstructor
public class DeviceRuleController {

    private final DeviceRuleService ruleService;

    @Operation(summary = "分页查询规则", description = "权限：当前未接入登录态（规划中）")
    @GetMapping("/list")
    public R<PageResult<DeviceRule>> list(@Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") long page,
                                        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") long pageSize,
                                        @Parameter(description = "名称（模糊匹配）") @RequestParam(required = false) String name,
                                        @Parameter(description = "状态：1-启用 0-禁用") @RequestParam(required = false) Integer status) {
        return R.ok(ruleService.list(page, pageSize, name, status));
    }

    @Operation(summary = "创建规则", description = "权限：当前未接入登录态（规划中）")
    @PostMapping
    public R<DeviceRule> create(@RequestBody DeviceRule rule) {
        return R.ok(ruleService.create(rule));
    }

    @Operation(summary = "更新/删除规则", description = "权限：当前未接入登录态（规划中）")
    @PutMapping("/{id}")
    public R<DeviceRule> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody DeviceRule patch) {
        return R.ok(ruleService.update(id, patch));
    }

    @Operation(summary = "更新/删除规则", description = "权限：当前未接入登录态（规划中）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        ruleService.delete(id);
        return R.ok();
    }
}
