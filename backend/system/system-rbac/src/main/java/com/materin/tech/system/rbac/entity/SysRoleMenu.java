package com.materin.tech.system.rbac.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 角色-菜单关联。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_role_menu")
public class SysRoleMenu {

    @Id(keyType = KeyType.None)
    private Long roleId;

    @Id(keyType = KeyType.None)
    private Long menuId;
}
