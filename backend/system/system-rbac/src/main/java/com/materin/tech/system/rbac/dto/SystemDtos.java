package com.materin.tech.system.rbac.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** 系统管理 DTO：对齐前端 vben 精简版字段（name/deptId/status/remark/permissions）。 */
public final class SystemDtos {

    private SystemDtos() {
    }

    @Schema(description = "用户创建/更新请求")
    public record UserUpsertRequest(
            @Schema(description = "用户名（同时作为登录名）") String name,
            @Schema(description = "部门 ID") Long deptId,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "备注") String remark,
            @Schema(description = "授权菜单 ID 集合（当前版本忽略）") List<Long> permissions) {
    }

    @Schema(description = "用户列表项")
    public record UserItem(
            @Schema(description = "用户 ID") Long id,
            @Schema(description = "名称（昵称）") String name,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "部门 ID") Long deptId,
            @Schema(description = "备注") String remark,
            @Schema(description = "创建时间（yyyy-MM-dd HH:mm:ss）") String createTime) {
    }

    @Schema(description = "角色创建/更新请求")
    public record RoleUpsertRequest(
            @Schema(description = "角色名") String name,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "备注") String remark,
            @Schema(description = "授权菜单 ID 集合") List<Long> permissions) {
    }

    @Schema(description = "角色列表项")
    public record RoleItem(
            @Schema(description = "角色 ID") Long id,
            @Schema(description = "角色名") String name,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "备注") String remark,
            @Schema(description = "创建时间") String createTime,
            @Schema(description = "授权菜单 ID 集合") List<Long> permissions) {
    }

    @Schema(description = "菜单创建/更新请求")
    public record MenuUpsertRequest(
            @Schema(description = "菜单名") String name,
            @Schema(description = "父级菜单 ID（根为空/0）") Long pid,
            @Schema(description = "类型：catalog-目录 menu-菜单 embedded-内嵌 link-外链 button-按钮") String type,
            @Schema(description = "路由路径") String path,
            @Schema(description = "前端组件路径") String component,
            @Schema(description = "权限标识（如 AC_100100）") String authCode,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "排序值") Integer sort,
            @Schema(description = "元信息（title/icon/order/activeIcon/link/iframeSrc 等）") Map<String, Object> meta) {
    }

    @Schema(description = "菜单树节点")
    public record MenuNode(
            @Schema(description = "菜单 ID") Long id,
            @Schema(description = "父级菜单 ID") Long pid,
            @Schema(description = "菜单名") String name,
            @Schema(description = "路由路径") String path,
            @Schema(description = "类型") String type,
            @Schema(description = "前端组件路径") String component,
            @Schema(description = "权限标识") String authCode,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "排序值") Integer sort,
            @Schema(description = "元信息") Map<String, Object> meta,
            @Schema(description = "子菜单") List<MenuNode> children) {
    }

    @Schema(description = "部门创建/更新请求")
    public record DeptUpsertRequest(
            @Schema(description = "部门名") String name,
            @Schema(description = "父级部门 ID（根为空/0）") Long pid,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "备注") String remark) {
    }

    @Schema(description = "部门树节点")
    public record DeptNode(
            @Schema(description = "部门 ID") Long id,
            @Schema(description = "父级部门 ID") Long pid,
            @Schema(description = "部门名") String name,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "备注") String remark,
            @Schema(description = "子部门") List<DeptNode> children) {
    }
}
