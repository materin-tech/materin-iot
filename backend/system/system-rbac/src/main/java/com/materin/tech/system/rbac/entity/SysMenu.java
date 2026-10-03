package com.materin.tech.system.rbac.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 菜单 / 权限点（树形）。meta 以 JSON 存储（title/icon/order 等）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_menu")
public class SysMenu extends BaseEntity {

    private String name;

    private Long pid;

    /** catalog / menu / embedded / link / button */
    private String type;

    /** 路由路径 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 后端权限标识，如 AC_100100 */
    private String authCode;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    /** 排序 */
    private Integer sort;

    /** 元信息 JSON（title/icon/order/activeIcon/link/iframeSrc 等） */
    @Column("meta")
    private String metaJson;
}
