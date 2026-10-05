package com.materin.tech.component.iotdb;

import com.materin.tech.component.timeseries.TimeSeriesManager;
import org.apache.iotdb.session.pool.SessionPool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** IoTDB 时序组件装配：SessionPool + TimeSeriesManager 实现。 */
@Configuration
@ConditionalOnProperty(prefix = "materin.iotdb", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IotdbProperties.class)
public class IotdbAutoConfiguration {

    @Bean(destroyMethod = "close")
    public SessionPool iotdbSessionPool(IotdbProperties properties) {
        return new SessionPool.Builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .user(properties.getUsername())
                .password(properties.getPassword())
                .maxSize(properties.getMaxPoolSize())
                .build();
    }

    @Bean
    public TimeSeriesManager timeSeriesManager(SessionPool sessionPool, IotdbProperties properties) {
        return new IotdbTimeSeriesManager(sessionPool, properties.getDatabase());
    }
}
