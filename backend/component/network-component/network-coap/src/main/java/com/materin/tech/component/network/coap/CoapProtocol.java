package com.materin.tech.component.network.coap;

import com.materin.tech.common.spi.NetworkProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(CoapProperties.class)
public class CoapProtocol implements NetworkProtocol {

    @Override
    public String name() {
        return "coap";
    }

    @Override
    public void start() {
        log.info("CoAP 接入组件已启用（接入实现待开发）");
    }
}
