package com.materin.tech.component.dfx.core.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** DFX 指标字典：上报 key -> 汉字表述/单位/类型（展示与告警配置的口径来源）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("dfx_metric_config")
public class DfxMetricConfig extends BaseEntity {

    /** 上报指标 key（含 dfx_ 前缀），唯一 */
    private String metric;

    /** 汉字表述 */
    private String name;

    private String unit;

    /** double / long / text */
    private String dataType;

    private String description;
}
