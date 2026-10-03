package com.materin.tech.common.spi;

/**
 * 跨组件协作 SPI：下行命令发布（由 network-mqtt 实现）。
 * 服务无状态：发布本身不需要会话，设备应答经 requestId + Redis 信箱回传发起实例。
 */
public interface CommandPublisher {

    /**
     * 发布下行命令。
     *
     * @return true=已提交到 broker；false=MQTT 客户端不可用/未连接
     */
    boolean publish(String topic, byte[] payload);
}
