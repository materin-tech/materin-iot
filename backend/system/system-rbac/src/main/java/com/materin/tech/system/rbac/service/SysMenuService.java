package com.materin.tech.system.rbac.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.rbac.dto.SystemDtos.MenuNode;
import com.materin.tech.system.rbac.dto.SystemDtos.MenuUpsertRequest;
import com.materin.tech.system.rbac.entity.SysMenu;
import com.materin.tech.system.rbac.mapper.SysMenuMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 菜单管理：树形 CRUD，meta JSON 存取。 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private static final TypeReference<Map<String, Object>> META_TYPE = new TypeReference<>() {
    };

    private final SysMenuMapper sysMenuMapper;
    private final ObjectMapper objectMapper;

    public List<MenuNode> listTree() {
        List<SysMenu> all = sysMenuMapper.selectAll();
        return buildTree(all);
    }

    public boolean isNameExists(String name, Long id) {
        QueryWrapper wrapper = QueryWrapper.create().eq("name", name);
        if (id != null) {
            wrapper.ne("id", id);
        }
        return sysMenuMapper.selectCountByQuery(wrapper) > 0;
    }

    public boolean isPathExists(String path, Long id) {
        QueryWrapper wrapper = QueryWrapper.create().eq("path", path);
        if (id != null) {
            wrapper.ne("id", id);
        }
        return sysMenuMapper.selectCountByQuery(wrapper) > 0;
    }

    public MenuNode create(MenuUpsertRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw BizException.badRequest("菜单名不能为空");
        }
        SysMenu menu = new SysMenu();
        apply(menu, request);
        sysMenuMapper.insert(menu);
        return toNode(menu);
    }

    public MenuNode update(Long id, MenuUpsertRequest request) {
        SysMenu menu = requireMenu(id);
        apply(menu, request);
        sysMenuMapper.update(menu);
        return toNode(menu);
    }

    public void delete(Long id) {
        requireMenu(id);
        long children = sysMenuMapper.selectCountByQuery(QueryWrapper.create().eq("pid", id));
        if (children > 0) {
            throw BizException.badRequest("存在子菜单，无法删除");
        }
        sysMenuMapper.deleteById(id);
    }

    private void apply(SysMenu menu, MenuUpsertRequest request) {
        if (request.name() != null) {
            menu.setName(request.name());
        }
        if (request.pid() != null) {
            menu.setPid(request.pid());
        }
        if (StringUtils.hasText(request.type())) {
            menu.setType(request.type());
        }
        if (request.path() != null) {
            menu.setPath(request.path());
        }
        if (request.component() != null) {
            menu.setComponent(request.component());
        }
        if (request.authCode() != null) {
            menu.setAuthCode(request.authCode());
        }
        if (request.status() != null) {
            menu.setStatus(request.status());
        }
        if (request.sort() != null) {
            menu.setSort(request.sort());
        }
        if (request.meta() != null) {
            try {
                menu.setMetaJson(objectMapper.writeValueAsString(request.meta()));
            } catch (Exception e) {
                throw new BizException(500, "菜单 meta 序列化失败");
            }
        }
    }

    private List<MenuNode> buildTree(List<SysMenu> all) {
        Map<Long, List<SysMenu>> byPid = all.stream()
                .collect(Collectors.groupingBy(m -> m.getPid() == null ? 0L : m.getPid()));
        return childrenOf(0L, byPid);
    }

    private List<MenuNode> childrenOf(Long pid, Map<Long, List<SysMenu>> byPid) {
        List<MenuNode> nodes = new java.util.ArrayList<>();
        for (SysMenu menu : byPid.getOrDefault(pid, List.of()).stream()
                .sorted(Comparator.comparing(m -> m.getSort() == null ? 0 : m.getSort()))
                .toList()) {
            nodes.add(new MenuNode(menu.getId(), menu.getPid(), menu.getName(), menu.getPath(),
                    menu.getType(), menu.getComponent(), menu.getAuthCode(), menu.getStatus(),
                    menu.getSort(), readMeta(menu.getMetaJson()),
                    childrenOf(menu.getId(), byPid)));
        }
        return nodes;
    }

    private SysMenu requireMenu(Long id) {
        SysMenu menu = sysMenuMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
        if (menu == null) {
            throw BizException.notFound("菜单不存在: " + id);
        }
        return menu;
    }

    private MenuNode toNode(SysMenu menu) {
        return new MenuNode(menu.getId(), menu.getPid(), menu.getName(), menu.getPath(),
                menu.getType(), menu.getComponent(), menu.getAuthCode(), menu.getStatus(),
                menu.getSort(), readMeta(menu.getMetaJson()), List.of());
    }

    private Map<String, Object> readMeta(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, META_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
