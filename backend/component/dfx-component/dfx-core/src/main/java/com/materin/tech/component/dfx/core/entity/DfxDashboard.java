package com.materin.tech.component.dfx.core.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** DFX 产品 Dashboard 配置（类 Grafana：图表布局完全由 config_json 驱动，设备经产品关联）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("dfx_dashboard")
public class DfxDashboard extends BaseEntity {

    private Long productId;

    private String productName;

    private String name;

    /** {"charts":[{title,type,unit,yMax,agg,interval,metrics:[{key,name}]}]} */
    private String configJson;

    private String remark;
}
