package com.materin.tech.system.rbac.service;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.rbac.dto.SystemDtos.RoleItem;
import com.materin.tech.system.rbac.dto.SystemDtos.RoleUpsertRequest;
import com.materin.tech.system.rbac.entity.SysRole;
import com.materin.tech.system.rbac.entity.SysRoleMenu;
import com.materin.tech.system.rbac.mapper.SysRoleMapper;
import com.materin.tech.system.rbac.mapper.SysRoleMenuMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** 角色管理：对齐前端 /system/role 接口契约（permissions = 菜单 id 集合）。 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public PageResult<RoleItem> list(long page, long pageSize, String name, String remark, Integer status) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StringUtils.hasText(name)) {
            wrapper.like("name", name);
        }
        if (StringUtils.hasText(remark)) {
            wrapper.like("remark", remark);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        Page<SysRole> result = sysRoleMapper.paginate(Page.of(page, pageSize), wrapper);
        List<RoleItem> items = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(items, result.getTotalRow());
    }

    @Transactional
    public RoleItem create(RoleUpsertRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw BizException.badRequest("角色名不能为空");
        }
        SysRole role = new SysRole();
        role.setName(request.name());
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setRemark(request.remark());
        sysRoleMapper.insert(role);
        savePermissions(role.getId(), request.permissions());
        return toItem(role);
    }

    @Transactional
    public RoleItem update(Long id, RoleUpsertRequest request) {
        SysRole role = requireRole(id);
        if (StringUtils.hasText(request.name())) {
            role.setName(request.name());
        }
        role.setStatus(request.status() == null ? role.getStatus() : request.status());
        role.setRemark(request.remark());
        sysRoleMapper.update(role);
        sysRoleMenuMapper.deleteByQuery(QueryWrapper.create().eq("role_id", id));
        savePermissions(id, request.permissions());
        return toItem(role);
    }

    @Transactional
    public void delete(Long id) {
        requireRole(id);
        sysRoleMapper.deleteById(id);
        sysRoleMenuMapper.deleteByQuery(QueryWrapper.create().eq("role_id", id));
    }

    private void savePermissions(Long roleId, List<Long> menuIds) {
        if (menuIds == null) {
            return;
        }
        for (Long menuId : menuIds) {
            sysRoleMenuMapper.insertSelective(new SysRoleMenu(roleId, menuId));
        }
    }

    private SysRole requireRole(Long id) {
        SysRole role = sysRoleMapper.selectOneById(id);
        if (role == null) {
            throw BizException.notFound("角色不存在: " + id);
        }
        return role;
    }

    private RoleItem toItem(SysRole role) {
        List<Long> menuIds = sysRoleMenuMapper.selectListByQuery(
                        QueryWrapper.create().eq("role_id", role.getId()))
                .stream().map(SysRoleMenu::getMenuId).toList();
        String createTime = role.getCreateTime() == null ? null : role.getCreateTime().format(TS);
        return new RoleItem(role.getId(), role.getName(), role.getStatus(),
                role.getRemark(), createTime, menuIds);
    }
}
