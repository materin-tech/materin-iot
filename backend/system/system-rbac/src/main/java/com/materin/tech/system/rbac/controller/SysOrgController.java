package com.materin.tech.system.rbac.controller;

import com.materin.tech.common.core.ApiVersions;
import com.materin.tech.common.core.R;
import com.materin.tech.system.rbac.dto.SystemDtos.OrgNode;
import com.materin.tech.system.rbac.dto.SystemDtos.OrgUpsertRequest;
import com.materin.tech.system.rbac.service.SysOrgService;
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

/** 组织管理接口（原部门管理）：/api/v1/system/org。 */
@Tag(name = "组织管理")
@RestController
@RequestMapping("/system/org")
@RequiredArgsConstructor
public class SysOrgController {

    private final SysOrgService sysOrgService;

    @Operation(summary = "查询组织树", description = "权限：需登录，功能权限码 AC_100010（组织管理）")
    @GetMapping("/list")
    public R<List<OrgNode>> list() {
        return R.ok(sysOrgService.listTree());
    }

    @Operation(summary = "创建组织", description = "权限：需登录，功能权限码 AC_100010（组织管理）")
    @PostMapping
    public R<OrgNode> create(@RequestBody OrgUpsertRequest request) {
        return R.ok(sysOrgService.create(request));
    }

    @Operation(summary = "更新/删除组织", description = "权限：需登录，功能权限码 AC_100010（组织管理）")
    @PutMapping("/{id}")
    public R<OrgNode> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody OrgUpsertRequest request) {
        return R.ok(sysOrgService.update(id, request));
    }

    @Operation(summary = "更新/删除组织", description = "权限：需登录，功能权限码 AC_100010（组织管理）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        sysOrgService.delete(id);
        return R.ok();
    }
}
