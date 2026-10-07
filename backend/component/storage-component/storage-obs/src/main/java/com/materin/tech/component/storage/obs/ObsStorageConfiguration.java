package com.materin.tech.component.storage.obs;

import com.materin.tech.component.storage.StorageClient;
import com.materin.tech.component.storage.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配：materin.storage.type=OBS 时激活（与其它实现互斥，多实现共存由 core 守卫拦截）。 */
@Configuration
@ConditionalOnProperty(prefix = "materin.storage", name = "type", havingValue = "OBS")
public class ObsStorageConfiguration {

    @Bean
    public StorageClient obsStorageClient(StorageProperties properties) {
        return new ObsStorageClient(properties);
    }
}
