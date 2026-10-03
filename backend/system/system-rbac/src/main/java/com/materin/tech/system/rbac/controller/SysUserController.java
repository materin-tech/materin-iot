package com.materin.tech.system.rbac.controller;

import com.materin.tech.common.core.ApiVersions;
import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.core.R;
import com.materin.tech.system.rbac.dto.SystemDtos.UserItem;
import com.materin.tech.system.rbac.dto.SystemDtos.UserUpsertRequest;
import com.materin.tech.system.rbac.service.SysUserService;
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

/** 系统用户管理接口：/api/v1/system/user。 */
@Tag(name = "系统用户")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @Operation(summary = "分页查询用户", description = "权限：需登录，功能权限码 AC_100100（用户管理）")
    @GetMapping("/list")
    public R<PageResult<UserItem>> list(@Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") long page,
                                        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") long pageSize,
                                        @Parameter(description = "名称（模糊匹配）") @RequestParam(required = false) String name,
                                        @Parameter(description = "备注（模糊匹配）") @RequestParam(required = false) String remark,
                                        @Parameter(description = "状态：1-启用 0-禁用") @RequestParam(required = false) Integer status,
                                        @Parameter(description = "部门 ID") @RequestParam(required = false) Long deptId,
                                        @Parameter(description = "创建时间起") @RequestParam(required = false) String startTime,
                                        @Parameter(description = "创建时间止") @RequestParam(required = false) String endTime) {
        return R.ok(sysUserService.list(page, pageSize, name, remark, status, deptId, startTime, endTime));
    }

    @Operation(summary = "创建用户", description = "权限：需登录，功能权限码 AC_100100（用户管理）")
    @PostMapping
    public R<UserItem> create(@RequestBody UserUpsertRequest request) {
        return R.ok(sysUserService.create(request));
    }

    @Operation(summary = "更新/删除用户", description = "权限：需登录，功能权限码 AC_100100（用户管理）")
    @PutMapping("/{id}")
    public R<UserItem> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody UserUpsertRequest request) {
        return R.ok(sysUserService.update(id, request));
    }

    @Operation(summary = "更新/删除用户", description = "权限：需登录，功能权限码 AC_100100（用户管理）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        sysUserService.delete(id);
        return R.ok();
    }
}
