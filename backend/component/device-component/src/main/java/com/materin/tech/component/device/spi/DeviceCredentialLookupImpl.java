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
package com.materin.tech.component.device.spi;

import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.ProductLookup;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 设备凭证与归属 SPI 实现（供 MQTT 鉴权/ACL 与消息消费）。 */
@Component
public class DeviceCredentialLookupImpl implements DeviceCredentialLookup {

    private final DeviceMapper deviceMapper;
    private final ProductLookup productLookup;
    private final com.materin.tech.component.device.spi.DeviceOnlineWriter onlineWriter;

    public DeviceCredentialLookupImpl(DeviceMapper deviceMapper,
                                      ObjectProvider<ProductLookup> productLookup,
                                      com.materin.tech.component.device.spi.DeviceOnlineWriter onlineWriter) {
        this.deviceMapper = deviceMapper;
        this.productLookup = productLookup.getIfAvailable();
        this.onlineWriter = onlineWriter;
    }

    @Override
    public Optional<DeviceCredential> findByKey(String deviceKey) {
        Device device = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("device_key", deviceKey));
        if (device == null || device.getSecret() == null) {
            return Optional.empty();
        }
        return Optional.of(new DeviceCredential(device.getDeviceKey(),
                device.getId(), device.getSecret(), device.getName()));
    }

    @Override
    public void touchOnline(Long deviceId, java.time.LocalDateTime time) {
        onlineWriter.touch(deviceId);
    }

    @Override
    public Optional<String> findKeyById(Long deviceId) {
        Device device = deviceMapper.selectOneByQuery(QueryWrapper.create().eq("id", deviceId));
        return device == null ? Optional.empty() : Optional.of(device.getDeviceKey());
    }

    @Override
    public Optional<Long> findProductIdByKey(String deviceKey) {
        Device device = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("device_key", deviceKey));
        return device == null || device.getProductId() == null
                ? Optional.empty() : Optional.of(device.getProductId());
    }

    @Override
    public Optional<String> findProductKeyByKey(String deviceKey) {
        Device device = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("device_key", deviceKey));
        if (device == null || device.getProductId() == null || productLookup == null) {
            return Optional.empty();
        }
        return productLookup.findById(String.valueOf(device.getProductId()))
                .map(ProductLookup.ProductSummary::productKey);
    }

    @Override
    public Optional<String> findDeviceTypeByKey(String deviceKey) {
        Device device = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("device_key", deviceKey));
        return device == null ? Optional.empty() : Optional.ofNullable(device.getDeviceType());
    }
}
