package com.materin.tech.common.spi;

/** 跨组件协作 SPI：产品侧统计已接入设备数（由 product-component 实现）。 */
public interface ProductDeviceCounter {

    void onDeviceAdded(String productId);

    void onDeviceRemoved(String productId);
}
