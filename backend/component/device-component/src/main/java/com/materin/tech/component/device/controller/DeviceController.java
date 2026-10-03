package com.materin.tech.component.device.controller;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.core.R;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.service.DeviceCommandService;
import com.materin.tech.component.device.service.DeviceService;
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

import java.util.List;
import java.util.Map;

/** 设备管理接口：/api/v1/device。 */
@Tag(name = "设备管理")
@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceCommandService commandService;

    @Operation(summary = "分页查询设备", description = "权限：当前未接入登录态（规划中）；命令下发依赖设备在线状态")
    @GetMapping("/list")
    public R<PageResult<Device>> list(@Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") long page,
                                        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") long pageSize,
                                        @Parameter(description = "名称（模糊匹配）") @RequestParam(required = false) String name,
                                        @Parameter(description = "设备标识") @RequestParam(required = false) String deviceKey,
                                        @Parameter(description = "产品 ID") @RequestParam(required = false) Long productId,
                                        @Parameter(description = "状态：1-启用 0-禁用") @RequestParam(required = false) Integer status) {
        return R.ok(deviceService.list(page, pageSize, name, deviceKey, productId, status));
    }

    @Operation(summary = "创建设备（生成设备密钥）", description = "权限：当前未接入登录态（规划中）")
    @PostMapping
    public R<Device> create(@RequestBody Map<String, Object> payload) {
        return R.ok(deviceService.create(payload));
    }

    @Operation(summary = "批量导入设备", description = "权限：当前未接入登录态（规划中）；命令下发依赖设备在线状态")
    @PostMapping("/import")
    public R<Map<String, Integer>> imports(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault(
                "items", List.of());
        return R.ok(deviceService.importDevices(items));
    }

    /** 下发命令并等待设备应答（requestId + Redis 信箱跨实例回传）。 */
    @Operation(summary = "下发命令并同步等待设备应答", description = "权限：当前未接入登录态（规划中）；命令下发依赖设备在线状态")
    @PostMapping("/{id}/cmd")
    public R<String> sendCommand(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody Map<String, Object> command) {
        return R.ok(commandService.send(id, command));
    }

    @Operation(summary = "更新/删除设备", description = "权限：当前未接入登录态（规划中）；命令下发依赖设备在线状态")
    @PutMapping("/{id}")
    public R<Device> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return R.ok(deviceService.update(id, payload));
    }

    @Operation(summary = "更新/删除设备", description = "权限：当前未接入登录态（规划中）；命令下发依赖设备在线状态")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        deviceService.delete(id);
        return R.ok();
    }
}
