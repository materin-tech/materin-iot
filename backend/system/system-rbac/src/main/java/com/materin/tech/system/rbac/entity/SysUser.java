package com.materin.tech.system.rbac.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    /** BCrypt 散列，接口层永不返回。 */
    private String password;

    private String nickname;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    private Long deptId;

    private String remark;
}
