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

/** 跨组件协作 SPI：MQTT 鉴权时查设备凭证（由 device-component 实现）。 */
public interface DeviceCredentialLookup {

    Optional<DeviceCredential> findByKey(String deviceKey);

    record DeviceCredential(String deviceKey, Long deviceId, String secret, String deviceName) {
    }

    /** 记录设备最近在线时间（DB 节流落库），默认空实现便于无设备模块时独立运行。 */
    default void touchOnline(Long deviceId, java.time.LocalDateTime time) {
    }

    /** 按设备 ID 查 deviceKey（下行命令时使用）。 */
    default Optional<String> findKeyById(Long deviceId) {
        return Optional.empty();
    }

    /** 按设备 ID 查设备名（DFX 规则/采集目标创建时冗余回填，default 空实现便于独立运行）。 */
    default Optional<String> findNameById(Long deviceId) {
        return Optional.empty();
    }

    /** 按设备 Key 查所属产品的 productKey（MQTT ACL namespace 校验）。 */
    default Optional<String> findProductKeyByKey(String deviceKey) {
        return Optional.empty();
    }

    /** 按设备 Key 查所属产品 ID（DFX 时序模型维度）。 */
    default Optional<Long> findProductIdByKey(String deviceKey) {
        return Optional.empty();
    }

    /** 按设备 Key 查设备类型（DIRECT/GATEWAY/CHILD），网关 ACL 判定用。 */
    default Optional<String> findDeviceTypeByKey(String deviceKey) {
        return Optional.empty();
    }

    /** 设备状态统计（DFX 大盘用）：total/online/offline/inactive，默认空实现。 */
    default DeviceStatusStats statusStats() {
        return new DeviceStatusStats(0, 0, 0, 0);
    }

    record DeviceStatusStats(long total, long online, long offline, long inactive) {
    }
}
