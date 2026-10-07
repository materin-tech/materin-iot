package com.materin.tech.system.rbac.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 组织（树形，原部门）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_org")
public class SysOrg extends BaseEntity {

    private String name;

    private Long pid;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    private String remark;
}
