package com.materin.tech.common.spi;

import java.util.Optional;

/** 跨组件协作 SPI：设备侧反查产品信息（由 product-component 实现）。 */
public interface ProductLookup {

    Optional<ProductSummary> findById(String productId);

    record ProductSummary(String id, String name, String productKey) {
    }
}
