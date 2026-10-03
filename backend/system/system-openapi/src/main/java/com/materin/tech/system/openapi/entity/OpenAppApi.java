package com.materin.tech.system.openapi.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 应用-接口授权关联。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("open_app_api")
public class OpenAppApi {

    @Id(keyType = KeyType.None)
    private Long appId;

    @Id(keyType = KeyType.None)
    private Long apiId;
}
