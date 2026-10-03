package com.materin.tech.component.network.coap;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CoAP 接入配置。 */
@Data
@ConfigurationProperties(prefix = "materin.network.coap")
public class CoapProperties {

    private boolean enabled = true;

    private int port = 5683;
}
