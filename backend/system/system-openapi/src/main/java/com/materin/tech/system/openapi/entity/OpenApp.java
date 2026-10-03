package com.materin.tech.system.openapi.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 开发者应用（AK/SK）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("open_app")
public class OpenApp extends BaseEntity {

    private String name;

    /** Access Key（公开标识） */
    private String appKey;

    /** Secret Key（私密凭证，仅创建时完整返回一次） */
    private String appSecret;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    private String remark;
}
