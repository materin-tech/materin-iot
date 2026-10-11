/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.gateway.core;

import java.util.Map;
import java.util.Set;

/**
 * Connector SPI：一种"子设备接入方式"的抽象（对齐 ThingsBoard Gateway 的 converter/connector 概念）。
 * 实现类负责从外部协议/通道采集数据并经 {@link ConnectorContext} 上行，
 * 同时接收平台下发的命令。实现须自行忽略不认识的 deviceKey。
 */
public interface Connector {

    /** connector 实例标识（来自配置 id），用于日志与排障 */
    String id();

    /** connector 类型标识（demo / mqtt-bridge / 自定义） */
    String type();

    /**
     * 启动 connector：建立外部连接、声明子设备并开始采集。
     * 抛异常仅表示该 connector 启动失败，网关会跳过它继续运行。
     */
    void start(ConnectorContext ctx);

    /** 停止 connector：释放外部连接与线程资源 */
    void stop();

    /**
     * 平台下行命令分发入口。
     *
     * @param deviceKey 目标子设备
     * @param data      命令数据（来自 downlink payload 的 data 字段）
     * @param requestId 命令请求 ID，回执时原样带回
     */
    void onCommand(String deviceKey, Map<String, Object> data, String requestId);

    /**
     * connector 确定管理的子设备 deviceKey 集合；返回空集合表示动态发现（下行命令将广播给它）。
     */
    default Set<String> devices() {
        return Set.of();
    }
}
