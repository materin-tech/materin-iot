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

/**
 * Connector 上行出口：由 {@link GatewayService} 实现，
 * 负责组装平台协议 payload（自动注入 gateway 字段）并经平台 MQTT 发布。
 * 全部方法均为尽力而为：发布失败只记日志，不向 connector 抛出。
 */
public interface ConnectorContext {

    /**
     * 子设备上线声明；productKey 可选，声明子设备归属独立产品，缺省继承网关所属产品。
     */
    void connect(String deviceKey, String name, String productKey);

    /** 子设备上线声明（子设备归属网关所属产品） */
    default void connect(String deviceKey, String name) {
        connect(deviceKey, name, null);
    }

    /** 子设备离线声明 */
    void disconnect(String deviceKey);

    /** 子设备遥测（时间戳取当前毫秒） */
    void telemetry(String deviceKey, Map<String, Object> values);

    /** 子设备遥测（显式毫秒时间戳） */
    void telemetry(String deviceKey, Map<String, Object> values, long ts);

    /** 子设备属性上报 */
    void attributes(String deviceKey, Map<String, Object> values);

    /**
     * 命令回执：向平台回复下行命令的执行结果（成功数据或错误信息），
     * 经 requestId 关联到平台下发的 replyTopic。
     */
    void reply(String deviceKey, String requestId, Map<String, Object> data);
}
