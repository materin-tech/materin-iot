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
package com.materin.tech.component.device.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 设备告警（生命周期：0-活跃 → 1-已确认 → 2-已恢复）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("device_alert")
public class DeviceAlert extends BaseEntity {

    private Long deviceId;

    private String deviceName;

    /** info / warn / error */
    private String level;

    private String content;

    /** 关联全链路追踪 ID（存在链路上下文时记录，调度线程产生的告警为新生成 ID） */
    private String traceId;

    /** 0-活跃 1-已确认 2-已恢复 */
    private Integer status;

    /** 触发规则 ID（规则告警的合并键） */
    private Long ruleId;

    /** 告警指标（非规则告警的合并键，如 snmp_poll/offline） */
    private String metric;

    /** 最近一次越限值 */
    private String lastValue;

    /** 同规则累计触发次数 */
    private Integer triggerCount;

    /** 最近触发时间 */
    private LocalDateTime lastTime;

    /** 确认时间 */
    private LocalDateTime ackTime;

    /** 恢复时间 */
    private LocalDateTime clearTime;

    /** 恢复原因 */
    private String clearReason;
}
