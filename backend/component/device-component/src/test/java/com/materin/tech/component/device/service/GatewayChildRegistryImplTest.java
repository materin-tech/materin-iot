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

import com.materin.tech.component.device.entity.Device;
import com.materin.tech.component.device.mapper.DeviceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 网关子设备登记 SPI 实现测试：新建/归属绑定/类型修正/离线守卫。 */
class GatewayChildRegistryImplTest {

    private DeviceMapper deviceMapper;
    private GatewayChildRegistryImpl registry;

    @BeforeEach
    void setUp() {
        deviceMapper = Mockito.mock(DeviceMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<com.materin.tech.common.spi.ProductLookup> productLookup =
                Mockito.mock(ObjectProvider.class);
        Mockito.when(productLookup.getIfAvailable()).thenReturn(null);
        registry = new GatewayChildRegistryImpl(deviceMapper, productLookup);
    }

    private Device gateway() {
        Device gw = new Device();
        gw.setId(10L);
        gw.setDeviceKey("gw-1");
        gw.setDeviceType("GATEWAY");
        gw.setProductId(100L);
        gw.setProductName("标准网关产品");
        return gw;
    }

    private Device existingChild() {
        Device child = new Device();
        child.setId(20L);
        child.setDeviceKey("child-1");
        child.setDeviceType("DIRECT");
        child.setGatewayId(99L);
        child.setProductId(100L);
        child.setProductName("标准网关产品");
        return child;
    }

    @Test
    void registerOrAttach_shouldInsertChildInheritingGatewayProduct() {
        Mockito.when(deviceMapper.selectOneByQuery(Mockito.any(QueryWrapper.class)))
                .thenReturn(gateway(), (Device) null);

        Long id = registry.registerOrAttach("child-1", "温湿度", 10L);

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).insert(captor.capture());
        Device inserted = captor.getValue();
        assertThat(inserted.getDeviceType()).isEqualTo("CHILD");
        assertThat(inserted.getGatewayId()).isEqualTo(10L);
        assertThat(inserted.getProductId()).isEqualTo(100L);
        assertThat(inserted.getProductName()).isEqualTo("标准网关产品");
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(inserted.getName()).isEqualTo("温湿度");
        assertThat(id).isEqualTo(inserted.getId());
    }

    @Test
    void registerOrAttach_shouldRebindGatewayAndFixType() {
        Device child = existingChild();
        Mockito.when(deviceMapper.selectOneByQuery(Mockito.any(QueryWrapper.class)))
                .thenReturn(gateway(), child);

        Long id = registry.registerOrAttach("child-1", null, 10L);

        verify(deviceMapper).update(child);
        assertThat(child.getGatewayId()).isEqualTo(10L);
        assertThat(child.getDeviceType()).isEqualTo("CHILD");
        assertThat(id).isEqualTo(20L);
    }

    @Test
    void registerOrAttach_shouldNotUpdateWhenAlreadyAttached() {
        Device child = existingChild();
        child.setGatewayId(10L);
        child.setDeviceType("CHILD");
        Mockito.when(deviceMapper.selectOneByQuery(Mockito.any(QueryWrapper.class)))
                .thenReturn(gateway(), child);

        registry.registerOrAttach("child-1", null, 10L);

        verify(deviceMapper, Mockito.never()).update(Mockito.any(Device.class));
    }

    @Test
    void markOffline_shouldSkipNonChildDevices() {
        Device direct = new Device();
        direct.setId(30L);
        direct.setDeviceKey("direct-1");
        direct.setDeviceType("DIRECT");
        Mockito.when(deviceMapper.selectOneByQuery(Mockito.any(QueryWrapper.class)))
                .thenReturn(direct);
        registry.markOffline("direct-1");
        verify(deviceMapper, Mockito.never()).update(Mockito.any(Device.class));
    }
}
