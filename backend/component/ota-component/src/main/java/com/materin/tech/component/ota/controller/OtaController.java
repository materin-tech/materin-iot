package com.materin.tech.component.ota.controller;

import com.materin.tech.common.core.R;
import com.materin.tech.component.ota.entity.OtaPackage;
import com.materin.tech.component.ota.entity.OtaTask;
import com.materin.tech.component.ota.entity.OtaTaskDevice;
import com.materin.tech.component.ota.service.OtaPackageService;
import com.materin.tech.component.ota.service.OtaTaskService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** OTA 接口：固件包管理（存储走 storage 中间件）+ 升级任务 + 设备明细。 */
@Tag(name = "OTA 升级")
@RestController
@RequestMapping("/ota")
@RequiredArgsConstructor
public class OtaController {

    private final OtaPackageService packageService;
    private final OtaTaskService taskService;

    @Operation(summary = "固件包列表")
    @GetMapping("/package/list")
    public R<Page<OtaPackage>> packages(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long pageSize,
                                        @RequestParam(required = false) Long productId,
                                        @RequestParam(required = false) String name) {
        return R.ok(packageService.list(page, pageSize, productId, name));
    }

    @Operation(summary = "上传固件包（multipart，文件经存储中间件落对象存储）")
    @PostMapping("/package")
    public R<OtaPackage> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam Long productId,
                                @RequestParam String productName,
                                @RequestParam String name,
                                @RequestParam String version,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) String module,
                                @RequestParam(required = false) String signMethod,
                                @RequestParam(required = false) String description) {
        OtaPackage meta = new OtaPackage();
        meta.setProductId(productId);
        meta.setProductName(productName);
        meta.setName(name);
        meta.setVersion(version);
        meta.setType(type == null ? "firmware" : type);
        meta.setModule(module);
        meta.setSignMethod(signMethod);
        meta.setDescription(description);
        return R.ok(packageService.upload(file, meta));
    }

    @Operation(summary = "获取固件包下载地址（预签名）")
    @GetMapping("/package/{id}/download-url")
    public R<Map<String, Object>> downloadUrl(@PathVariable Long id,
                                              @RequestParam(defaultValue = "3600") int expireSeconds) {
        return R.ok(Map.of("url", packageService.downloadUrl(id, expireSeconds)));
    }

    @Operation(summary = "删除固件包（含对象存储文件）")
    @DeleteMapping("/package/{id}")
    public R<Void> deletePackage(@PathVariable Long id) {
        packageService.delete(id);
        return R.ok(null);
    }

    @Operation(summary = "升级任务列表")
    @GetMapping("/task/list")
    public R<Page<OtaTask>> tasks(@RequestParam(defaultValue = "1") long page,
                                  @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(taskService.list(page, pageSize));
    }

    @Operation(summary = "创建升级任务并推送（body: {packageId, taskName, deviceIds:[...]}）")
    @PostMapping("/task")
    public R<OtaTask> createTask(@RequestBody Map<String, Object> body) {
        OtaTask task = new OtaTask();
        task.setPackageId(Long.valueOf(String.valueOf(body.get("packageId"))));
        task.setTaskName((String) body.getOrDefault("taskName", "OTA 升级"));
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("deviceIds");
        List<Long> deviceIds = ids == null ? List.of()
                : ids.stream().map(Number::longValue).toList();
        return R.ok(taskService.createTask(task, deviceIds));
    }

    @Operation(summary = "任务设备明细（分页，含状态/进度）")
    @GetMapping("/task/{id}/devices")
    public R<Page<OtaTaskDevice>> taskDevices(@PathVariable Long id,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long pageSize,
                                              @RequestParam(required = false) Integer status) {
        return R.ok(taskService.taskDevices(page, pageSize, id, status));
    }

    @Operation(summary = "失败重推")
    @PostMapping("/task/device/{id}/retry")
    public R<Map<String, Object>> retry(@PathVariable Long id) {
        return R.ok(Map.of("pushed", taskService.retry(id)));
    }
}
