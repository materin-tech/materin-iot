package com.materin.tech.common.spi;

/** 跨组件协作 SPI：开发者应用 AK/SK 校验（由 system-openapi 实现）。 */
public interface OpenAppAuthChecker {

    boolean existsByAppKey(String appKey);

    boolean matches(String appKey, String appSecret);
}
