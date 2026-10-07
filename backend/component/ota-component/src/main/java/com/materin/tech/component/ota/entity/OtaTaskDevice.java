package com.materin.tech.component.ota.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** OTA 任务-设备明细（状态机：0 待推送 1 已推送 2 下载中 3 升级中 4 成功 5 失败）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("ota_task_device")
public class OtaTaskDevice extends BaseEntity {

    private Long taskId;

    private Long packageId;

    private Long deviceId;

    private String deviceKey;

    private String deviceName;

    /** 0 待推送 1 已推送 2 下载中 3 升级中 4 成功 5 失败 */
    private Integer status;

    /** 设备上报进度 0-100 */
    private Integer progress;

    /** 最近一次明细（失败原因等） */
    private String message;

    private Integer pushCount;
}
