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
 * DFX 健康指标统一入口 SPI（docs/dfx-monitoring-design.md §1.1）。
 * 各协议适配器（MQTT dfx 分支 / HTTP ingest / SNMP 轮询 / 二期 LwM2M）统一调用，
 * 由 device-component 的管道实现（校验→Redis 最新值→时序库→告警评估）。
 * 实现方必须兜底异常：指标管道故障只降级，绝不阻断接入链路。
 */
public interface DfxSink {

    /**
     * 保存一帧 DFX 指标。
     *
     * @param deviceKey 设备标识（实现方反查设备与产品）
     * @param time      epoch 毫秒
     * @param source    接入协议来源（mqtt / http / snmp / lw m2m），仅用于追踪与展示
     * @param metrics   指标键值（键 = 统一指标字典名，值 = 数值或字符串）
     */
    void saveDfx(String deviceKey, long time, String source, Map<String, Object> metrics);
}
