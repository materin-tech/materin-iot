package com.materin.tech.system.rbac.config;

import com.materin.tech.system.rbac.entity.SysOrg;
import com.materin.tech.system.rbac.entity.SysMenu;
import com.materin.tech.system.rbac.entity.SysRole;
import com.materin.tech.system.rbac.entity.SysRoleMenu;
import com.materin.tech.system.rbac.entity.SysUser;
import com.materin.tech.system.rbac.entity.SysUserRole;
import com.materin.tech.system.rbac.mapper.SysMenuMapper;
import com.materin.tech.system.rbac.mapper.SysRoleMapper;
import com.materin.tech.system.rbac.mapper.SysRoleMenuMapper;
import com.materin.tech.system.rbac.mapper.SysUserMapper;
import com.materin.tech.system.rbac.mapper.SysUserRoleMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次启动种子数据：admin 用户、admin 角色、系统管理菜单（含权限码）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacDataInitializer implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final com.materin.tech.system.rbac.mapper.SysOrgMapper sysOrgMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        Long adminId = ensureAdmin();
        Long adminRoleId = ensureAdminRole();
        if (adminId != null && adminRoleId != null) {
            linkUserRole(adminId, adminRoleId);
        }
        // 菜单数据由 Flyway 迁移负责（V2/V4）；这里只保证 admin 角色拥有全部菜单
        grantAllMenusToAdmin(adminRoleId);
        ensureOrgs();
        log.info("RBAC 种子数据就绪（admin / 123456，首次登录将要求修改密码）");
    }

    private Long ensureAdmin() {
        if (sysUserMapper.selectCountByQuery(QueryWrapper.create().eq("username", "admin")) > 0) {
            return null;
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setNickname("Admin");
        admin.setPassword(encoder.encode("123456"));
        // 等保三级：种子账号不设置 password_update_time，首次登录强制修改密码
        admin.setStatus(1);
        admin.setRemark("内置管理员");
        sysUserMapper.insert(admin);
        return admin.getId();
    }

    private Long ensureAdminRole() {
        if (sysRoleMapper.selectCountByQuery(QueryWrapper.create().eq("name", "admin")) > 0) {
            return sysRoleMapper.selectOneByQuery(QueryWrapper.create().eq("name", "admin")).getId();
        }
        SysRole role = new SysRole();
        role.setName("admin");
        role.setStatus(1);
        role.setRemark("超级管理员");
        sysRoleMapper.insert(role);
        return role.getId();
    }

    private void linkUserRole(Long userId, Long roleId) {
        if (sysUserRoleMapper.selectCountByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("role_id", roleId)) == 0) {
            sysUserRoleMapper.insert(new SysUserRole(userId, roleId));
        }
    }

    /** admin 角色绑定全部菜单（幂等；新菜单由迁移入库后，重启即自动授权给 admin）。 */
    private void grantAllMenusToAdmin(Long adminRoleId) {
        if (adminRoleId == null) {
            return;
        }
        for (SysMenu menu : sysMenuMapper.selectAll()) {
            if (sysRoleMenuMapper.selectCountByQuery(QueryWrapper.create()
                    .eq("role_id", adminRoleId).eq("menu_id", menu.getId())) == 0) {
                sysRoleMenuMapper.insert(new SysRoleMenu(adminRoleId, menu.getId()));
            }
        }
    }

    private void ensureOrgs() {
        if (sysOrgMapper.selectCountByQuery(QueryWrapper.create()) > 0) {
            return;
        }
        SysOrg hq = org("总部", 0L);
        SysOrg rd = org("研发部", hq.getId());
        SysOrg mkt = org("市场部", hq.getId());
        org("华东分部", 0L);
        org("产品组", rd.getId());
        org("销售组", mkt.getId());
    }

    private SysOrg org(String name, Long pid) {
        SysOrg org = new SysOrg();
        org.setName(name);
        org.setPid(pid);
        org.setStatus(1);
        sysOrgMapper.insert(org);
        return org;
    }
}
