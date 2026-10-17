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

import com.materin.tech.common.exception.BizException;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.component.dfx.core.entity.DfxAlertRule;
import com.materin.tech.component.dfx.core.mapper.DfxAlertRuleMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

/** DFX 阈值告警规则 CRUD（v2：比较符/持续时长/恢复条件/消息模板，设备级+产品级作用域）。 */
@Service
@RequiredArgsConstructor
public class DfxAlertRuleService {

    private static final Set<String> COMPARATORS = Set.of("gt", "gte", "lt", "lte", "eq", "ne", "between");

    private final DfxAlertRuleMapper mapper;
    private final DeviceCredentialLookup credentialLookup;

    public Page<DfxAlertRule> list(long page, long pageSize, Long deviceId, Long productId,
                                   String keyword, String level, Integer enabled) {
        QueryWrapper qw = QueryWrapper.create();
        if (deviceId != null) {
            qw.eq("device_id", deviceId);
        }
        if (productId != null) {
            qw.eq("product_id", productId);
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword + "%";
            qw.and(new com.mybatisflex.core.query.QueryColumn("metric").like(like)
                    .or(new com.mybatisflex.core.query.QueryColumn("device_name").like(like)));
        }
        if (StringUtils.hasText(level)) {
            qw.eq("level", level);
        }
        if (enabled != null) {
            qw.eq("enabled", enabled);
        }
        return mapper.paginate(page, pageSize, qw.orderBy("id", false));
    }

    public DfxAlertRule create(DfxAlertRule rule) {
        validate(rule);
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        if (rule.getSuppressSeconds() == null) {
            rule.setSuppressSeconds(600);
        }
        if (rule.getDurationSeconds() == null) {
            rule.setDurationSeconds(0);
        }
        if (!StringUtils.hasText(rule.getLevel())) {
            rule.setLevel("info");
        }
        if (!StringUtils.hasText(rule.getDeviceName()) && rule.getDeviceId() != null) {
            rule.setDeviceName(credentialLookup.findNameById(rule.getDeviceId()).orElse(""));
        }
        if (!StringUtils.hasText(rule.getDeviceName())) {
            rule.setDeviceName("");
        }
        mapper.insert(rule);
        return rule;
    }

    public DfxAlertRule update(Long id, DfxAlertRule patch) {
        DfxAlertRule db = requireById(id);
        if (patch.getDeviceId() != null) {
            db.setDeviceId(patch.getDeviceId());
        }
        if (patch.getDeviceName() != null) {
            db.setDeviceName(patch.getDeviceName());
        }
        if (patch.getProductId() != null) {
            db.setProductId(patch.getProductId());
        }
        if (patch.getClearComparator() != null) {
            db.setClearComparator(patch.getClearComparator());
        }
        if (patch.getClearThreshold() != null) {
            db.setClearThreshold(patch.getClearThreshold());
        }
        if (patch.getMessageTemplate() != null) {
            db.setMessageTemplate(patch.getMessageTemplate());
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

    // ------------------------------------------------------------ 校验

    private void validate(DfxAlertRule rule) {
        if (!StringUtils.hasText(rule.getMetric())) {
            throw BizException.badRequest("规则指标不能为空");
        }
        String cmp = rule.getComparator();
        if (!StringUtils.hasText(cmp)) {
            throw BizException.badRequest("规则比较符不能为空/comparator required");
        }
        if (!COMPARATORS.contains(cmp)) {
            throw BizException.badRequest("非法比较符: " + cmp);
        }
        if ("between".equals(cmp) && rule.getThreshold2() == null) {
            throw BizException.badRequest("between 条件必须配置 threshold2 上界");
        }
        if (rule.getThreshold() == null) {
            throw BizException.badRequest("规则阈值不能为空");
        }
        if (rule.getDeviceId() == null && rule.getProductId() == null) {
            throw BizException.badRequest("设备级与产品级作用域至少配置其一");
        }
        if (StringUtils.hasText(rule.getClearComparator())
                && !COMPARATORS.contains(rule.getClearComparator())) {
            throw BizException.badRequest("非法恢复条件比较符: " + rule.getClearComparator());
        }
        if (StringUtils.hasText(rule.getClearComparator()) && rule.getClearThreshold() == null) {
            throw BizException.badRequest("已配置恢复条件比较符，恢复阈值不能为空");
        }
        if (rule.getLevel() != null && !Set.of("info", "warn", "error").contains(rule.getLevel())) {
            throw BizException.badRequest("非法告警级别: " + rule.getLevel());
        }
    }

    private DfxAlertRule requireById(Long id) {
        DfxAlertRule db = mapper.selectOneById(id);
        if (db == null) {
            throw BizException.notFound("规则不存在: " + id);
        }
        return db;
    }
}
