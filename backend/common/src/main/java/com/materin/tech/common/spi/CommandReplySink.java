package com.materin.tech.common.spi;

/**
 * 跨组件协作 SPI：命令回执投递（由 device-component 实现）。
 * 存储策略集中在实现侧：小报文直接进 Redis 信箱，大报文落库 + Redis 只放引用（防大 key）。
 */
public interface CommandReplySink {

    /** @param requestId 命令关联 ID；payload 原始应答字节 */
    void deliver(String requestId, byte[] payload);

    /** 应答引用解码：发起方按引用取回真实报文。 */
    String resolve(String deliveredPayload);
}
