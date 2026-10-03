package com.materin.tech.component.device.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备告警。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("device_alert")
public class DeviceAlert extends BaseEntity {

    private Long deviceId;

    private String deviceName;

    /** info / warn / error */
    private String level;

    private String content;

    /** 0-未处理 1-已处理 */
    private Integer status;
}
