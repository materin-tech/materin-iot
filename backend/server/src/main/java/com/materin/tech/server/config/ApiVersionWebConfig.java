package com.materin.tech.server.config;

import com.materin.tech.common.core.ApiVersions;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 统一 API 版本前缀：所有 com.materin.tech 包下的 RestController
 * 挂载到 /api/{version} 下（当前强制 v1）。
 */
@Configuration
public class ApiVersionWebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(ApiVersions.V1_PREFIX,
                handlerType -> handlerType.isAnnotationPresent(RestController.class)
                        && handlerType.getPackageName().startsWith("com.materin.tech"));
    }
}
