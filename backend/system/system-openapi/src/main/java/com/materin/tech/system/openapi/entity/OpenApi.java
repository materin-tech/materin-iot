package com.materin.tech.system.openapi.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 平台接口清单（源自 OpenAPI/swagger 同步）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("open_api")
public class OpenApi extends BaseEntity {

    /** GET/POST/PUT/DELETE */
    private String method;

    private String path;

    private String summary;

    /** swagger tag 分组 */
    private String tag;
}
