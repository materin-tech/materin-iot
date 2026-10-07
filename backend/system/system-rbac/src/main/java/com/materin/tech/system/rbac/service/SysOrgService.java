package com.materin.tech.system.rbac.service;

import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.rbac.dto.SystemDtos.OrgNode;
import com.materin.tech.system.rbac.dto.SystemDtos.OrgUpsertRequest;
import com.materin.tech.system.rbac.entity.SysOrg;
import com.materin.tech.system.rbac.mapper.SysOrgMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 组织管理：对齐前端 /system/org 接口契约（树形）。 */
@Service
@RequiredArgsConstructor
public class SysOrgService {

    private final SysOrgMapper sysOrgMapper;

    public List<OrgNode> listTree() {
        List<SysOrg> all = sysOrgMapper.selectAll()
                .stream().sorted(Comparator.comparing(SysOrg::getId)).toList();
        return buildTree(all);
    }

    public OrgNode create(OrgUpsertRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw BizException.badRequest("组织名不能为空");
        }
        SysOrg org = new SysOrg();
        org.setName(request.name());
        org.setPid(request.pid());
        org.setStatus(request.status() == null ? 1 : request.status());
        org.setRemark(request.remark());
        sysOrgMapper.insert(org);
        return new OrgNode(org.getId(), org.getPid(), org.getName(),
                org.getStatus(), org.getRemark(), List.of());
    }

    public OrgNode update(Long id, OrgUpsertRequest request) {
        SysOrg org = requireOrg(id);
        if (StringUtils.hasText(request.name())) {
            org.setName(request.name());
        }
        org.setPid(request.pid() == null ? org.getPid() : request.pid());
        org.setStatus(request.status() == null ? org.getStatus() : request.status());
        org.setRemark(request.remark());
        sysOrgMapper.update(org);
        return new OrgNode(org.getId(), org.getPid(), org.getName(),
                org.getStatus(), org.getRemark(), List.of());
    }

    public void delete(Long id) {
        requireOrg(id);
        long children = sysOrgMapper.selectCountByQuery(QueryWrapper.create().eq("pid", id));
        if (children > 0) {
            throw BizException.badRequest("存在子组织，无法删除");
        }
        sysOrgMapper.deleteById(id);
    }

    private List<OrgNode> buildTree(List<SysOrg> all) {
        Map<Long, List<SysOrg>> byPid = all.stream()
                .collect(Collectors.groupingBy(d -> d.getPid() == null ? 0L : d.getPid()));
        List<OrgNode> roots = new ArrayList<>();
        for (SysOrg root : byPid.getOrDefault(0L, List.of())) {
            roots.add(new OrgNode(root.getId(), root.getPid(), root.getName(),
                    root.getStatus(), root.getRemark(), buildChildren(root.getId(), byPid)));
        }
        return roots;
    }

    private List<OrgNode> buildChildren(Long pid, Map<Long, List<SysOrg>> byPid) {
        List<OrgNode> nodes = new ArrayList<>();
        for (SysOrg child : byPid.getOrDefault(pid, List.of())) {
            nodes.add(new OrgNode(child.getId(), child.getPid(), child.getName(),
                    child.getStatus(), child.getRemark(), buildChildren(child.getId(), byPid)));
        }
        nodes.sort(Comparator.comparing(OrgNode::id));
        return nodes;
    }

    private SysOrg requireOrg(Long id) {
        SysOrg org = sysOrgMapper.selectOneById(id);
        if (org == null) {
            throw BizException.notFound("组织不存在: " + id);
        }
        return org;
    }
}
