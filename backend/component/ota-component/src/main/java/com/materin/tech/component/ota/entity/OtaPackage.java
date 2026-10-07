package com.materin.tech.component.ota.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** OTA 固件/升级包（文件实体存对象存储，表只存元数据 + storage key）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("ota_package")
public class OtaPackage extends BaseEntity {

    private String name;

    private Long productId;

    private String productName;

    /** firmware / software / config */
    private String type;

    /** 模块（可选，如 mcu / esp32） */
    private String module;

    /** 目标版本 */
    private String version;

    private Long size;

    /** 校验方式：md5 / sha256 */
    private String signMethod;

    private String signValue;

    /** 对象存储 key（通用，经 StorageClient 抽象存取） */
    private String storageKey;

    private String contentType;

    private String description;

    /** 0-草稿 1-已发布 */
    private Integer status;
}
