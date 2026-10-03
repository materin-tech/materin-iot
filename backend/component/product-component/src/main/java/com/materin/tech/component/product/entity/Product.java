package com.materin.tech.component.product.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 产品。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("product")
public class Product extends BaseEntity {

    private String name;

    /** 产品唯一标识，设备连接时使用 */
    private String productKey;

    /** 品类 */
    private String category;

    /** 接入协议 */
    private String protocol;

    /** 0-开发中 1-已发布 */
    private Integer status;

    /** 来源：standard / custom */
    private String source;

    /** 已接入设备数（由设备组件通过 SPI 增减） */
    private Integer deviceCount;

    private String remark;
}
