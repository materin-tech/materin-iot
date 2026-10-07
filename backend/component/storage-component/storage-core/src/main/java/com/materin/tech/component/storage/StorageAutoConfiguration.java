package com.materin.tech.component.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 存储装配：无任何实现子模块（或 enabled=false）时不装配 StorageClient，
 * 上层经 ObjectProvider 判空降级——组件可独立运行，与 timeseries 同构。
 */
@Configuration
@ConditionalOnProperty(prefix = "materin.storage", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    // 实现子模块各自提供 StorageClient Bean（按 materin.storage.type 条件装配，互斥唯一）。
    // 使用方一律 ObjectProvider<StorageClient> 判空降级。
}
