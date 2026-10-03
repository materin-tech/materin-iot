package com.materin.tech.component.device.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备台账。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("device")
public class Device extends BaseEntity {

    private String name;

    /** 设备唯一标识，用于协议层寻址（MQTT username） */
    private String deviceKey;

    /** 设备密钥（MQTT password），创建时生成 */
    private String secret;

    /** 0 未激活 / 1 在线 / 2 离线 */
    private Integer status;

    /** 所属产品 */
    private Long productId;

    /** 冗余产品名，列表展示用 */
    private String productName;

    /** 固件版本 */
    private String firmware;

    /** 最近在线时间 */
    private String lastOnline;

    private String remark;
}
