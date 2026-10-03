package com.materin.tech.system.openapi.controller;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.openapi.dto.OpenApiDtos.AppCreatedItem;
import com.materin.tech.system.openapi.dto.OpenApiDtos.AppItem;
import com.materin.tech.system.openapi.dto.OpenApiDtos.AppUpsertRequest;
import com.materin.tech.system.openapi.dto.OpenApiDtos.ApiAuthRequest;
import com.materin.tech.system.openapi.service.OpenAppService;
import com.materin.tech.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 开发者应用管理接口：/api/v1/open/apps。 */
@Tag(name = "开发者应用")
@RestController
@RequestMapping("/open/apps")
public class OpenAppController {

    private final OpenAppService appService;

    public OpenAppController(OpenAppService appService) {
        this.appService = appService;
    }

    @Operation(summary = "分页查询开发者应用", description = "权限：需登录（开发者中心管理面）")
    @GetMapping("/list")
    public R<PageResult<AppItem>> list(@Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") long page,
                                        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") long pageSize,
                                        @Parameter(description = "名称（模糊匹配）") @RequestParam(required = false) String name,
                                        @Parameter(description = "状态：1-启用 0-禁用") @RequestParam(required = false) Integer status) {
        return R.ok(appService.list(page, pageSize, name, status));
    }

    @Operation(summary = "创建应用（响应含 AK/SK，SK 仅此一次返回）", description = "权限：需登录（开发者中心管理面）")
    @PostMapping
    public R<AppCreatedItem> create(@RequestBody AppUpsertRequest request) {
        return R.ok(appService.create(request));
    }

    /** 查看应用完整凭证（AK/SK）与信息。 */
    @Operation(summary = "查看应用凭证（AK/SK）", description = "权限：需登录（开发者中心管理面）")
    @GetMapping("/{id}/credential")
    public R<AppCreatedItem> credential(@PathVariable Long id) {
        return R.ok(appService.getCredentials(id));
    }

    /** 重置 SK：旧 SK 立即失效，仅本次响应返回完整新 SK。 */
    @Operation(summary = "重置 SK（旧 SK 立即失效）", description = "权限：需登录（开发者中心管理面）")
    @PostMapping("/{id}/secret/reset")
    public R<AppCreatedItem> resetSecret(@Parameter(description = "资源 ID") @PathVariable Long id) {
        return R.ok(appService.resetSecret(id));
    }

    @Operation(summary = "更新/删除应用", description = "权限：需登录（开发者中心管理面）")
    @PutMapping("/{id}")
    public R<AppItem> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody AppUpsertRequest request) {
        return R.ok(appService.update(id, request));
    }

    @Operation(summary = "更新/删除应用", description = "权限：需登录（开发者中心管理面）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        appService.delete(id);
        return R.ok();
    }

    /** 接口授权：全量替换勾选结果。 */
    @Operation(summary = "接口授权（全量替换）/ 已授权接口回显", description = "权限：需登录（开发者中心管理面）")
    @PutMapping("/{id}/apis")
    public R<Void> authApis(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody com.materin.tech.system.openapi.dto.OpenApiDtos.ApiAuthRequest request) {
        appService.authApis(id, request.apiIds());
        return R.ok();
    }

    /** 批量授权：把勾选接口增量授权给应用（已授权自动跳过）。 */
    @Operation(summary = "批量授权接口", description = "权限：需登录（开发者中心管理面）")
    @PostMapping("/{id}/apis/grant")
    public R<Integer> grantApis(@PathVariable Long id, @RequestBody ApiAuthRequest request) {
        return R.ok(appService.grantApis(id, request.apiIds()));
    }

    /** 批量取消授权。 */
    @Operation(summary = "批量取消授权接口", description = "权限：需登录（开发者中心管理面）")
    @PostMapping("/{id}/apis/revoke")
    public R<Integer> revokeApis(@PathVariable Long id, @RequestBody ApiAuthRequest request) {
        return R.ok(appService.revokeApis(id, request.apiIds()));
    }

    /** 该应用已授权接口 ID 集合。 */
    @Operation(summary = "接口授权（全量替换）/ 已授权接口回显", description = "权限：需登录（开发者中心管理面）")
    @GetMapping("/{id}/apis")
    public R<List<Long>> authorizedApis(@Parameter(description = "资源 ID") @PathVariable Long id) {
        return R.ok(appService.authorizedApiIds(id));
    }
}
