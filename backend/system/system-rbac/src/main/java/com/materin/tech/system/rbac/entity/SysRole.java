package com.materin.tech.system.rbac.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_role")
public class SysRole extends BaseEntity {

    private String name;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    private String remark;
}
