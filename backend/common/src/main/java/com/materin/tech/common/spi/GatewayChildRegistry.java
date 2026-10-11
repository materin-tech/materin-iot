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

import java.util.Optional;

/**
 * 网关子设备登记 SPI（common，network-mqtt 与 device-component 解耦）：
 * 网关经 gateway 主题族声明子设备上线时，由设备模块负责自动注册/归属绑定/上下线。
 * 实现方必须兜底异常：登记失败抛错由调用方（GatewayHandler）降级为拒绝该消息。
 */
public interface GatewayChildRegistry {

    /**
     * 子设备注册或归属绑定：不存在则自动注册（CHILD 类型、挂网关所属产品），存在则校验/更新网关归属。
     *
     * @param deviceKey       子设备标识
     * @param deviceName      子设备名称（可空，缺省用 deviceKey）
     * @param gatewayDeviceId 网关设备 ID
     * @return 子设备设备 ID
     */
    Long registerOrAttach(String deviceKey, String deviceName, Long gatewayDeviceId);

    /**
     * 带 productKey 的注册重载：网关声明子设备归属独立产品（产品-物模型-设备 逻辑），
     * productKey 为空时继承网关所属产品。
     */
    default Long registerOrAttach(String deviceKey, String deviceName, Long gatewayDeviceId,
                                  String productKey) {
        return registerOrAttach(deviceKey, deviceName, gatewayDeviceId);
    }

    /** 子设备离线（由网关 disconnect 声明触发）。 */
    default void markOffline(String deviceKey) {
    }
}
