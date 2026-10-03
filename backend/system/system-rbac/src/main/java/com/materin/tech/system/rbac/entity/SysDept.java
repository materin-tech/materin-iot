package com.materin.tech.system.rbac.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 部门（树形）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_dept")
public class SysDept extends BaseEntity {

    private String name;

    private Long pid;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    private String remark;
}
