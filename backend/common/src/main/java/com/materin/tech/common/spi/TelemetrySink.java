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
package com.materin.tech.common.spi;

import java.util.Map;

/**
 * 上行数据落时序库 SPI：network-mqtt 消费上行消息后回调，
 * 由设备组件实现"设备域 → 时序域"桥接（物模型解析列类型 + 时序写入）。
 *
 * <p>实现方必须自行兜底异常：时序库不可用只允许降级告警，不得阻断上行链路。
 */
public interface TelemetrySink {

    /** 属性遥测落时序库（values 为设备上报的原始键值对）。 */
    void saveTelemetry(String deviceKey, long time, Map<String, Object> values);

    /** 事件上报落时序库（payload 为事件原始报文，事件标识与数据由实现方宽容解析）。 */
    void saveEvent(String deviceKey, long time, Map<String, Object> payload);

    /** 方法调用记录落时序库（平台下发并在应答/超时闭环后回调）。 */
    void saveMethodRecord(Long deviceId, String methodId, String requestJson,
                          String responseJson, boolean success, long costMs);
}
