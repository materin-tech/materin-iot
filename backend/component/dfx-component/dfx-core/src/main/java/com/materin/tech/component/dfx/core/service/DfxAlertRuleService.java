package com.materin.tech.component.dfx.core.service;

import com.materin.tech.component.dfx.core.entity.DfxAlertRule;
import com.materin.tech.component.dfx.core.mapper.DfxAlertRuleMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** DFX 阈值告警规则 CRUD。 */
@Service
@RequiredArgsConstructor
public class DfxAlertRuleService {

    private final DfxAlertRuleMapper mapper;

    public Page<DfxAlertRule> list(long page, long pageSize, Long deviceId) {
        QueryWrapper qw = QueryWrapper.create();
        if (deviceId != null) {
            qw.eq("device_id", deviceId);
        }
        return mapper.paginate(page, pageSize, qw.orderBy("id", false));
    }

    public DfxAlertRule create(DfxAlertRule rule) {
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        if (rule.getSuppressSeconds() == null) {
            rule.setSuppressSeconds(600);
        }
        mapper.insert(rule);
        return rule;
    }

    public DfxAlertRule update(Long id, DfxAlertRule patch) {
        DfxAlertRule db = requireById(id);
        if (patch.getMetric() != null) {
            db.setMetric(patch.getMetric());
        }
        if (patch.getComparator() != null) {
            db.setComparator(patch.getComparator());
        }
        if (patch.getThreshold() != null) {
            db.setThreshold(patch.getThreshold());
        }
        if (patch.getLevel() != null) {
            db.setLevel(patch.getLevel());
        }
        if (patch.getSuppressSeconds() != null) {
            db.setSuppressSeconds(patch.getSuppressSeconds());
        }
        if (patch.getEnabled() != null) {
            db.setEnabled(patch.getEnabled());
        }
        if (patch.getRemark() != null) {
            db.setRemark(patch.getRemark());
        }
        mapper.update(db);
        return db;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private DfxAlertRule requireById(Long id) {
        DfxAlertRule db = mapper.selectOneById(id);
        if (db == null) {
            throw new com.materin.tech.common.exception.BizException(404, "规则不存在: " + id);
        }
        return db;
    }
}
