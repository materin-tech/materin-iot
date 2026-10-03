package com.materin.tech.component.device.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备规则。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("device_rule")
public class DeviceRule extends BaseEntity {

    private String name;

    /** 触发条件描述 */
    private String trigger;

    /** 执行动作描述 */
    private String action;

    /** 0-停用 1-启用 */
    private Integer status;

    private String remark;
}
