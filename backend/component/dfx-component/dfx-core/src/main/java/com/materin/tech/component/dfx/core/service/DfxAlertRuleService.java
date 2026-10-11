/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
