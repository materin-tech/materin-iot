package com.materin.tech.system.rbac.service;

import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.rbac.dto.SystemDtos.DeptNode;
import com.materin.tech.system.rbac.dto.SystemDtos.DeptUpsertRequest;
import com.materin.tech.system.rbac.entity.SysDept;
import com.materin.tech.system.rbac.mapper.SysDeptMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 部门管理：对齐前端 /system/dept 接口契约（树形）。 */
@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper sysDeptMapper;

    public List<DeptNode> listTree() {
        List<SysDept> all = sysDeptMapper.selectAll()
                .stream().sorted(Comparator.comparing(SysDept::getId)).toList();
        return buildTree(all);
    }

    public DeptNode create(DeptUpsertRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw BizException.badRequest("部门名不能为空");
        }
        SysDept dept = new SysDept();
        dept.setName(request.name());
        dept.setPid(request.pid());
        dept.setStatus(request.status() == null ? 1 : request.status());
        dept.setRemark(request.remark());
        sysDeptMapper.insert(dept);
        return new DeptNode(dept.getId(), dept.getPid(), dept.getName(),
                dept.getStatus(), dept.getRemark(), List.of());
    }

    public DeptNode update(Long id, DeptUpsertRequest request) {
        SysDept dept = requireDept(id);
        if (StringUtils.hasText(request.name())) {
            dept.setName(request.name());
        }
        dept.setPid(request.pid() == null ? dept.getPid() : request.pid());
        dept.setStatus(request.status() == null ? dept.getStatus() : request.status());
        dept.setRemark(request.remark());
        sysDeptMapper.update(dept);
        return new DeptNode(dept.getId(), dept.getPid(), dept.getName(),
                dept.getStatus(), dept.getRemark(), List.of());
    }

    public void delete(Long id) {
        requireDept(id);
        long children = sysDeptMapper.selectCountByQuery(QueryWrapper.create().eq("pid", id));
        if (children > 0) {
            throw BizException.badRequest("存在子部门，无法删除");
        }
        sysDeptMapper.deleteById(id);
    }

    private List<DeptNode> buildTree(List<SysDept> all) {
        Map<Long, List<SysDept>> byPid = all.stream()
                .collect(Collectors.groupingBy(d -> d.getPid() == null ? 0L : d.getPid()));
        List<DeptNode> roots = new ArrayList<>();
        for (SysDept root : byPid.getOrDefault(0L, List.of())) {
            roots.add(new DeptNode(root.getId(), root.getPid(), root.getName(),
                    root.getStatus(), root.getRemark(), buildChildren(root.getId(), byPid)));
        }
        return roots;
    }

    private List<DeptNode> buildChildren(Long pid, Map<Long, List<SysDept>> byPid) {
        List<DeptNode> nodes = new ArrayList<>();
        for (SysDept child : byPid.getOrDefault(pid, List.of())) {
            nodes.add(new DeptNode(child.getId(), child.getPid(), child.getName(),
                    child.getStatus(), child.getRemark(), buildChildren(child.getId(), byPid)));
        }
        nodes.sort(Comparator.comparing(DeptNode::id));
        return nodes;
    }

    private SysDept requireDept(Long id) {
        SysDept dept = sysDeptMapper.selectOneById(id);
        if (dept == null) {
            throw BizException.notFound("部门不存在: " + id);
        }
        return dept;
    }
}
