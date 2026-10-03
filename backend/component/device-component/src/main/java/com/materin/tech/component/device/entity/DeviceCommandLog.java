package com.materin.tech.component.device.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

/** 命令日志：大报文回执落库（Redis 只存引用），同时保留命令审计记录。 */
@Data
@Table("device_command_log")
public class DeviceCommandLog {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String requestId;

    private Long deviceId;

    /** direction: 1-下发 2-应答 */
    private Integer direction;

    private String payload;
}
