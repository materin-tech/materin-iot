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
package com.materin.tech.component.network.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.redis.RedisKeys;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.GatewayChildRegistry;
import com.materin.tech.common.spi.TelemetrySink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 网关主题族处理测试：connect 注册+置在线、telemetry 管道、disconnect 清在线标记。 */
class GatewayHandlerTest {

    private GatewayChildRegistry registry;
    private DeviceCredentialLookup lookup;
    private TelemetryHandler telemetryHandler;
    private StringRedisTemplate redis;
    private TelemetrySink sink;
    private GatewayHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = Mockito.mock(GatewayChildRegistry.class);
        lookup = Mockito.mock(DeviceCredentialLookup.class);
        telemetryHandler = Mockito.mock(TelemetryHandler.class);
        redis = Mockito.mock(StringRedisTemplate.class);
        sink = Mockito.mock(TelemetrySink.class);

        ObjectProvider<GatewayChildRegistry> childRegistry = Mockito.mock(ObjectProvider.class);
        Mockito.when(childRegistry.getIfAvailable()).thenReturn(registry);
        ObjectProvider<DeviceCredentialLookup> credentialLookup = Mockito.mock(ObjectProvider.class);
        Mockito.when(credentialLookup.getIfAvailable()).thenReturn(lookup);
        ObjectProvider<TelemetrySink> telemetrySink = Mockito.mock(ObjectProvider.class);
        Mockito.when(telemetrySink.getIfAvailable()).thenReturn(sink);

        Mockito.when(lookup.findByKey("gw-1")).thenReturn(Optional.of(
                new DeviceCredentialLookup.DeviceCredential("gw-1", 10L, "secret", "网关")));

        handler = new GatewayHandler(new ObjectMapper(), childRegistry, credentialLookup,
                telemetrySink, telemetryHandler, redis);
    }

    @Test
    void handle_shouldRegisterAndTouchOnlineOnConnect() {
        handler.handle("materin/gateway/connect",
                "{\"gateway\":\"gw-1\",\"device\":\"child-1\",\"name\":\"温湿度子设备\"}".getBytes());

        verify(registry).registerOrAttach("child-1", "温湿度子设备", 10L, null);
        verify(telemetryHandler).touchOnline("child-1");
    }

    @Test
    void handle_shouldRejectPayloadWithoutGatewayField() {
        handler.handle("materin/gateway/connect", "{\"device\":\"child-1\"}".getBytes());

        verifyNoInteractions(registry);
    }

    @Test
    void handle_shouldRejectUnknownGateway() {
        handler.handle("materin/gateway/connect", "{\"gateway\":\"ghost-gw\",\"device\":\"child-1\"}".getBytes());

        verifyNoInteractions(registry);
    }

    @Test
    void handle_shouldPipeTelemetryThroughRedisAndSink() {
        handler.handle("materin/gateway/telemetry",
                "{\"gateway\":\"gw-1\",\"child-1\":[{\"ts\":123,\"values\":{\"temp\":25.5}}]}".getBytes());

        verify(telemetryHandler).storeTelemetry(Mockito.eq("child-1"),
                Mockito.argThat(m -> "25.5".equals(String.valueOf(m.get("temp")))));
        verify(telemetryHandler).touchOnline("child-1");
        verify(sink).saveTelemetry(Mockito.eq("child-1"), Mockito.eq(123L), Mockito.anyMap());
    }

    @Test
    void handle_shouldClearOnlineKeyOnDisconnect() {
        handler.handle("materin/gateway/disconnect", "{\"gateway\":\"gw-1\",\"device\":\"child-1\"}".getBytes());

        verify(registry).markOffline("child-1");
        verify(redis).delete(RedisKeys.DEVICE_ONLINE + "child-1");
        verifyNoInteractions(telemetryHandler);
    }

    @Test
    void handle_shouldTreatGatewaySelfTelemetryWithoutChildRegistration() {
        // 网关自身遥测（childKey == gateway deviceKey）：只走自身数据管道，绝不注册成子设备
        handler.handle("materin/gateway/telemetry",
                "{\"gateway\":\"gw-1\",\"gw-1\":[{\"ts\":123,\"values\":{\"uptime\":99}}]}".getBytes());

        verifyNoInteractions(registry);
        verify(telemetryHandler).storeTelemetry(Mockito.eq("gw-1"), Mockito.anyMap());
        verify(telemetryHandler).touchOnline("gw-1");
        verify(sink).saveTelemetry(Mockito.eq("gw-1"), Mockito.eq(123L), Mockito.anyMap());
    }

    @Test
    void handle_shouldTreatGatewaySelfAttributesWithoutChildRegistration() {
        handler.handle("materin/gateway/attributes",
                "{\"gateway\":\"gw-1\",\"gw-1\":{\"version\":\"0.1.0\"}}".getBytes());

        verifyNoInteractions(registry);
        verify(telemetryHandler).storeTelemetry(Mockito.eq("gw-1"), Mockito.anyMap());
    }
}
