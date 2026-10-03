package com.materin.tech.component.product.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 物模型：属性/方法/事件以 JSON 存储。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("thing_model")
public class ThingModel extends BaseEntity {

    private Long productId;

    /** 冗余产品名，列表展示用 */
    private String productName;

    private String version;

    /** 0-草稿 1-已发布 */
    private Integer status;

    private String remark;

    @Column("properties_json")
    private String propertiesJson;

    @Column("methods_json")
    private String methodsJson;

    @Column("events_json")
    private String eventsJson;
}
