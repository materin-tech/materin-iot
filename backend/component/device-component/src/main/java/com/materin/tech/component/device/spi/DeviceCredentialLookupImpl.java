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
                device.getId(), device.getSecret()));
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
    public Optional<String> findProductKeyByKey(String deviceKey) {
        Device device = deviceMapper.selectOneByQuery(
                QueryWrapper.create().eq("device_key", deviceKey));
        if (device == null || device.getProductId() == null || productLookup == null) {
            return Optional.empty();
        }
        return productLookup.findById(String.valueOf(device.getProductId()))
                .map(ProductLookup.ProductSummary::productKey);
    }
}
