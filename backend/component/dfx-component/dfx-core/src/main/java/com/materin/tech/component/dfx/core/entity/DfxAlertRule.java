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
package com.materin.tech.component.dfx.core.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** DFX 阈值告警规则（结构化条件，可被执行引擎评估；区别于自由文本的 device_rule）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("dfx_alert_rule")
public class DfxAlertRule extends BaseEntity {

    /** 设备级规则 ID（与产品级 product_id 二者至少其一） */
    private Long deviceId;

    private String deviceName;

    /** 产品级规则（该产品下所有设备适用，新设备自动生效；设备级规则此列 NULL） */
    private Long productId;

    /** 统一指标字典名（docs/dfx-monitoring-design.md §3），如 cpu_usage_pct */
    private String metric;

    /** gt/gte/lt/lte/eq/ne/between */
    private String comparator;

    private Double threshold;

    /** between 条件上界（comparator=between 时必填） */
    private Double threshold2;

    /** 持续秒数：0=立即触发；>0 需连续越限达到时长才触发（防瞬时毛刺误报） */
    private Integer durationSeconds;

    /** 恢复条件比较符（NULL=不自动恢复，需人工确认/由运维下线规则） */
    private String clearComparator;

    /** 恢复条件阈值 */
    private Double clearThreshold;

    /** 告警内容模板，占位符 {device}/{metric}/{value}/{threshold}（空=默认格式） */
    private String messageTemplate;

    /** info / warn / error */
    private String level;

    /** 同设备同指标告警抑制窗口（秒；也用于产品级规则按设备独立计时） */
    private Integer suppressSeconds;

    /** 1-启用 0-停用 */
    private Integer enabled;

    private String remark;
}
