package com.materin.tech.common.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：自增主键 + 审计时间（由数据库 now() 维护，避免 NULL 字段显式入库）。
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;
}
