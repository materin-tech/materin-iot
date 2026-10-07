package com.materin.tech.component.ota.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** OTA 升级任务（一次批量升级）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("ota_task")
public class OtaTask extends BaseEntity {

    private Long packageId;

    private String taskName;

    /** product=按产品统一升级 device=按设备单独升级 */
    private String scope;

    private Long productId;

    private String productName;

    /** 全量设备数 */
    private Integer deviceCount;

    /** 0-进行中 1-已完成 2-已取消 */
    private Integer status;
}
