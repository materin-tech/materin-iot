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

    private Long deviceId;

    private String deviceName;

    /** 统一指标字典名（docs/dfx-monitoring-design.md §3），如 cpu_usage_pct */
    private String metric;

    /** gt / lt */
    private String comparator;

    private Double threshold;

    /** info / warn / error */
    private String level;

    /** 同设备同指标告警抑制窗口（秒） */
    private Integer suppressSeconds;

    /** 1-启用 0-停用 */
    private Integer enabled;

    private String remark;
}
