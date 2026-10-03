package com.materin.tech.system.rbac.controller;

import com.materin.tech.common.core.ApiVersions;
import com.materin.tech.common.core.R;
import com.materin.tech.system.rbac.dto.SystemDtos.DeptNode;
import com.materin.tech.system.rbac.dto.SystemDtos.DeptUpsertRequest;
import com.materin.tech.system.rbac.service.SysDeptService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 部门管理接口：/api/v1/system/dept。 */
@Tag(name = "系统部门")
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;

    @Operation(summary = "查询部门树", description = "权限：需登录，功能权限码 AC_100010（部门管理）")
    @GetMapping("/list")
    public R<List<DeptNode>> list() {
        return R.ok(sysDeptService.listTree());
    }

    @Operation(summary = "创建部门", description = "权限：需登录，功能权限码 AC_100010（部门管理）")
    @PostMapping
    public R<DeptNode> create(@RequestBody DeptUpsertRequest request) {
        return R.ok(sysDeptService.create(request));
    }

    @Operation(summary = "更新/删除部门", description = "权限：需登录，功能权限码 AC_100010（部门管理）")
    @PutMapping("/{id}")
    public R<DeptNode> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody DeptUpsertRequest request) {
        return R.ok(sysDeptService.update(id, request));
    }

    @Operation(summary = "更新/删除部门", description = "权限：需登录，功能权限码 AC_100010（部门管理）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        sysDeptService.delete(id);
        return R.ok();
    }
}
