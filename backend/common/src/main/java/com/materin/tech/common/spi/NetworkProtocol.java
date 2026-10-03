package com.materin.tech.common.spi;

/**
 * 网络协议接入 SPI：每个协议组件提供一个实现并注册为 Spring Bean，
 * server 启动时收集所有实现完成协议装配。
 */
public interface NetworkProtocol {

    /** 协议标识，如 mqtt / coap */
    String name();

    /** 协议接入启动（连接 broker、监听端口等） */
    default void start() {
    }

    /** 协议接入停止 */
    default void stop() {
    }
}
