package com.materin.tech.component.device.service;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.component.device.entity.DeviceRule;
import com.materin.tech.component.device.mapper.DeviceRuleMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 设备规则。 */
@Service
@RequiredArgsConstructor
public class DeviceRuleService {

    private final DeviceRuleMapper ruleMapper;

    public PageResult<DeviceRule> list(long page, long pageSize, String name, Integer status) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StringUtils.hasText(name)) {
            String like = "%" + name + "%";
            wrapper.and(new QueryColumn("name").like(like)
                    .or(new QueryColumn("trigger").like(like)));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        Page<DeviceRule> result = ruleMapper.paginate(Page.of(page, pageSize), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotalRow());
    }

    public DeviceRule create(DeviceRule rule) {
        if (!StringUtils.hasText(rule.getName())) {
            throw BizException.badRequest("规则名不能为空");
        }
        if (rule.getStatus() == null) {
            rule.setStatus(1);
        }
        ruleMapper.insert(rule);
        return rule;
    }

    public DeviceRule update(Long id, DeviceRule patch) {
        DeviceRule rule = ruleMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
        if (rule == null) {
            throw BizException.notFound("规则不存在: " + id);
        }
        if (patch.getName() != null) {
            rule.setName(patch.getName());
        }
        if (patch.getTrigger() != null) {
            rule.setTrigger(patch.getTrigger());
        }
        if (patch.getAction() != null) {
            rule.setAction(patch.getAction());
        }
        if (patch.getStatus() != null) {
            rule.setStatus(patch.getStatus());
        }
        if (patch.getRemark() != null) {
            rule.setRemark(patch.getRemark());
        }
        ruleMapper.update(rule);
        return rule;
    }

    public void delete(Long id) {
        DeviceRule rule = ruleMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
        if (rule == null) {
            throw BizException.notFound("规则不存在: " + id);
        }
        ruleMapper.deleteById(id);
    }
}
