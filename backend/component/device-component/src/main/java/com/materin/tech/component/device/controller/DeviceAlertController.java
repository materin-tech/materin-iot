package com.materin.tech.component.device.controller;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.core.R;
import com.materin.tech.component.device.entity.DeviceAlert;
import com.materin.tech.component.device.service.DeviceAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 设备告警接口：/api/v1/device/alert。 */
@Tag(name = "设备告警")
@RestController
@RequestMapping("/device/alert")
@RequiredArgsConstructor
public class DeviceAlertController {

    private final DeviceAlertService alertService;

    @Operation(summary = "分页查询设备告警", description = "权限：当前未接入登录态（规划中）")
    @GetMapping("/list")
    public R<PageResult<DeviceAlert>> list(@Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") long page,
                                        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") long pageSize,
                                        @Parameter(description = "关键字（模糊匹配）") @RequestParam(required = false) String keyword,
                                        @Parameter(description = "告警级别 info/warn/error") @RequestParam(required = false) String level,
                                        @Parameter(description = "状态：1-启用 0-禁用") @RequestParam(required = false) Integer status) {
        return R.ok(alertService.list(page, pageSize, keyword, level, status));
    }

    @Operation(summary = "处理告警（标记已处理）", description = "权限：当前未接入登录态（规划中）")
    @PutMapping("/{id}")
    public R<DeviceAlert> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody DeviceAlert patch) {
        return R.ok(alertService.update(id, patch));
    }
}
