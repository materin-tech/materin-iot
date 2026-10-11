/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.tech.system.rbac.controller;

import com.materin.tech.common.core.ApiVersions;
import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.core.R;
import com.materin.tech.system.rbac.dto.SystemDtos.RoleItem;
import com.materin.tech.system.rbac.dto.SystemDtos.RoleUpsertRequest;
import com.materin.tech.system.rbac.service.SysRoleService;
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

/** 角色管理接口：/api/v1/system/role。 */
@Tag(name = "系统角色")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @Operation(summary = "分页查询角色", description = "权限：需登录，功能权限码 AC_100110（角色管理）")
    @GetMapping("/list")
    public R<PageResult<RoleItem>> list(@Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") long page,
                                        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") long pageSize,
                                        @Parameter(description = "名称（模糊匹配）") @RequestParam(required = false) String name,
                                        @Parameter(description = "备注（模糊匹配）") @RequestParam(required = false) String remark,
                                        @Parameter(description = "状态：1-启用 0-禁用") @RequestParam(required = false) Integer status) {
        return R.ok(sysRoleService.list(page, pageSize, name, remark, status));
    }

    @Operation(summary = "创建角色", description = "权限：需登录，功能权限码 AC_100110（角色管理）")
    @PostMapping
    public R<RoleItem> create(@RequestBody RoleUpsertRequest request) {
        return R.ok(sysRoleService.create(request));
    }

    @Operation(summary = "更新/删除角色", description = "权限：需登录，功能权限码 AC_100110（角色管理）")
    @PutMapping("/{id}")
    public R<RoleItem> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody RoleUpsertRequest request) {
        return R.ok(sysRoleService.update(id, request));
    }

    @Operation(summary = "更新/删除角色", description = "权限：需登录，功能权限码 AC_100110（角色管理）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        sysRoleService.delete(id);
        return R.ok();
    }
}
