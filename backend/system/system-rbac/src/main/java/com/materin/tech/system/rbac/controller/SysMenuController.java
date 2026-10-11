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
import com.materin.tech.common.core.R;
import com.materin.tech.system.rbac.dto.SystemDtos.MenuNode;
import com.materin.tech.system.rbac.dto.SystemDtos.MenuUpsertRequest;
import com.materin.tech.system.rbac.dto.SystemDtos.RouteNode;
import com.materin.tech.system.rbac.security.AuthInterceptor;
import com.materin.tech.system.rbac.security.CurrentUser;
import com.materin.tech.system.rbac.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

/** 菜单管理接口：/api/v1/system/menu。 */
@Tag(name = "系统菜单")
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @Operation(summary = "获取当前用户菜单路由", description = "权限：需登录；按角色-菜单绑定下发，前端 backend 模式动态建路由")
    @GetMapping("/routes")
    public R<List<RouteNode>> routes(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.ATTR_CURRENT_USER);
        return R.ok(sysMenuService.listRouteTreeForUser(current.userId()));
    }

    @Operation(summary = "查询菜单树", description = "权限：需登录，功能权限码 AC_100120（菜单管理）")
    @GetMapping("/list")
    public R<List<MenuNode>> list() {
        return R.ok(sysMenuService.listTree());
    }

    @Operation(summary = "校验菜单名是否存在", description = "权限：需登录，功能权限码 AC_100120（菜单管理）")
    @GetMapping("/name-exists")
    public R<Boolean> nameExists(@Parameter(description = "名称（模糊匹配）") @RequestParam String name, @Parameter(description = "资源 ID") @RequestParam(required = false) Long id) {
        return R.ok(sysMenuService.isNameExists(name, id));
    }

    @Operation(summary = "校验路由是否存在", description = "权限：需登录，功能权限码 AC_100120（菜单管理）")
    @GetMapping("/path-exists")
    public R<Boolean> pathExists(@Parameter(description = "路由路径") @RequestParam String path, @Parameter(description = "资源 ID") @RequestParam(required = false) Long id) {
        return R.ok(sysMenuService.isPathExists(path, id));
    }

    @Operation(summary = "创建菜单", description = "权限：需登录，功能权限码 AC_100120（菜单管理）")
    @PostMapping
    public R<MenuNode> create(@RequestBody MenuUpsertRequest request) {
        return R.ok(sysMenuService.create(request));
    }

    @Operation(summary = "更新/删除菜单", description = "权限：需登录，功能权限码 AC_100120（菜单管理）")
    @PutMapping("/{id}")
    public R<MenuNode> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody MenuUpsertRequest request) {
        return R.ok(sysMenuService.update(id, request));
    }

    @Operation(summary = "更新/删除菜单", description = "权限：需登录，功能权限码 AC_100120（菜单管理）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        sysMenuService.delete(id);
        return R.ok();
    }
}
