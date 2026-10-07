package com.materin.tech.component.storage.oss;

import com.materin.tech.component.storage.StorageClient;
import com.materin.tech.component.storage.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配：materin.storage.type=ALIYUN 时激活（与其它实现互斥）。 */
@Configuration
@ConditionalOnProperty(prefix = "materin.storage", name = "type", havingValue = "ALIYUN")
public class OssStorageConfiguration {

    @Bean
    public StorageClient ossStorageClient(StorageProperties properties) {
        return new OssStorageClient(properties);
    }
}
