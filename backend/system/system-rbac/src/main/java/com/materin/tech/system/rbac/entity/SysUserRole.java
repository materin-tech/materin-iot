package com.materin.tech.system.rbac.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户-角色关联。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_user_role")
public class SysUserRole {

    @Id(keyType = KeyType.None)
    private Long userId;

    @Id(keyType = KeyType.None)
    private Long roleId;
}
