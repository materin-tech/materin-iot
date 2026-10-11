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
package com.materin.tech.component.device.service;

import com.materin.tech.common.exception.BizException;
import com.materin.tech.common.spi.GatewayChildRegistry;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 网关子设备登记 SPI 实现：子设备上线声明时自动注册（CHILD 类型、挂网关所属产品），
 * 已存在则校验/更新网关归属与类型，离线声明时仅对 CHILD 设备落离线状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayChildRegistryImpl implements GatewayChildRegistry {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 设备类型常量（与 DeviceService/迁移列约束一致） */
    static final String TYPE_DIRECT = "DIRECT";
    static final String TYPE_GATEWAY = "GATEWAY";
    static final String TYPE_CHILD = "CHILD";

    private final DeviceMapper deviceMapper;
    private final ObjectProvider<com.materin.tech.common.spi.ProductLookup> productLookup;

    @Override
    public Long registerOrAttach(String deviceKey, String deviceName, Long gatewayDeviceId) {
        return registerOrAttach(deviceKey, deviceName, gatewayDeviceId, null);
    }

    @Override
    public Long registerOrAttach(String deviceKey, String deviceName, Long gatewayDeviceId,
                                 String productKey) {
        Device gateway = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", gatewayDeviceId));
        if (gateway == null) {
            throw BizException.badRequest("网关设备不存在: " + gatewayDeviceId);
        }
        Device child = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("device_key", deviceKey));
        if (child == null) {
            return insertChild(deviceKey, deviceName, gateway, productKey);
        }
        return attachChild(child, gateway);
    }

    @Override
    public void markOffline(String deviceKey) {
        Device device = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("device_key", deviceKey));
        // 网关自带心跳，DIRECT 设备不经网关声明离线，仅 CHILD 由网关代声明
        if (device == null || !TYPE_CHILD.equals(device.getDeviceType())) {
            return;
        }
        device.setStatus(2);
        device.setLastOnline(LocalDateTime.now().format(TS));
        deviceMapper.update(device);
        log.info("子设备离线: deviceKey={}", deviceKey);
    }

    /** 新设备自动注册：productKey 指定独立产品（产品-物模型-设备），缺省继承网关所属产品。 */
    private Long insertChild(String deviceKey, String deviceName, Device gateway,
                             String productKey) {
        Long productId = gateway.getProductId();
        String productName = gateway.getProductName();
        if (StringUtils.hasText(productKey) && productLookup.getIfAvailable() != null) {
            var lookup = productLookup.getIfAvailable();
            var summary = lookup == null ? null : lookup.findByKey(productKey).orElse(null);
            if (summary != null) {
                productId = Long.valueOf(summary.id());
                productName = summary.name();
            }
        }
        Device child = new Device();
        child.setName(StringUtils.hasText(deviceName) ? deviceName : deviceKey);
        child.setDeviceKey(deviceKey);
        child.setSecret(UUID.randomUUID().toString().replace("-", ""));
        child.setStatus(1);
        child.setProductId(productId);
        child.setProductName(productName);
        child.setDeviceType(TYPE_CHILD);
        child.setGatewayId(gateway.getId());
        deviceMapper.insert(child);
        log.info("子设备自动注册: deviceKey={}, gatewayId={}, productId={}",
                deviceKey, gateway.getId(), gateway.getProductId());
        return child.getId();
    }

    /** 已有设备归属绑定：修正网关归属与设备类型，无需变更时跳过更新。 */
    private Long attachChild(Device child, Device gateway) {
        boolean changed = false;
        if (!gateway.getId().equals(child.getGatewayId())) {
            child.setGatewayId(gateway.getId());
            changed = true;
        }
        if (!TYPE_CHILD.equals(child.getDeviceType())) {
            child.setDeviceType(TYPE_CHILD);
            changed = true;
        }
        if (changed) {
            deviceMapper.update(child);
            log.info("子设备归属绑定: deviceKey={}, gatewayId={}", child.getDeviceKey(), gateway.getId());
        }
        return child.getId();
    }
}
