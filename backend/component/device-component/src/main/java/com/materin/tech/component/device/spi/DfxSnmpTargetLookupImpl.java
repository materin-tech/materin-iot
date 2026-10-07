package com.materin.tech.component.device.spi;

import com.materin.tech.common.spi.DfxSnmpTargetLookup;
import com.materin.tech.component.device.mapper.DfxSnmpTargetMapper;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.entity.DfxSnmpTarget;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** DfxSnmpTargetLookup 实现：轮询器不感知表结构（SPI 接口隔离）。 */
@Component
public class DfxSnmpTargetLookupImpl implements DfxSnmpTargetLookup {

    private final DfxSnmpTargetMapper mapper;
    private final DeviceMapper deviceMapper;

    public DfxSnmpTargetLookupImpl(DfxSnmpTargetMapper mapper, DeviceMapper deviceMapper) {
        this.mapper = mapper;
        this.deviceMapper = deviceMapper;
    }

    @Override
    public List<SnmpTarget> findEnabled() {
        return mapper.selectListByQuery(QueryWrapper.create().eq("enabled", 1)).stream()
                .map(t -> new SnmpTarget(t.getDeviceId(), resolveDeviceKey(t.getDeviceId()),
                        t.getHost(), t.getPort() == null ? 161 : t.getPort(),
                        t.getCommunity(), t.getIntervalSeconds() == null ? 60 : t.getIntervalSeconds()))
                .toList();
    }

    private String resolveDeviceKey(Long deviceId) {
        Device d = deviceMapper.selectOneByQuery(QueryWrapper.create().eq("id", deviceId));
        return d == null ? null : d.getDeviceKey();
    }
}
