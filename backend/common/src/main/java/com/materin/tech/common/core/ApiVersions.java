package com.materin.tech.common.core;

/** API 版本前缀：全部控制器统一挂载在 /api/{version} 下。 */
public interface ApiVersions {

    String V1 = "v1";

    String V1_PREFIX = "/api/" + V1;
}
