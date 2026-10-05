package com.materin.tech.component.network.mqtt;

import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.OpenAppAuthChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** MQTT 接入判定三分流测试（MqttAccessPolicy 核心策略）。 */
class MqttAccessPolicyTest {

    private MqttProperties properties;
    private ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private ObjectProvider<OpenAppAuthChecker> appProvider;
    private MqttAccessPolicy policy;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new MqttProperties();
        credentialLookup = Mockito.mock(ObjectProvider.class);
        appProvider = Mockito.mock(ObjectProvider.class);
        policy = new MqttAccessPolicy(properties, credentialLookup, appProvider);
    }

    @Test
    void authenticate_shouldAllowServiceAccountWithMatchingPassword() {
        assertThat(policy.authenticate("materin-svc-consumer", "materin-svc-dev-password")).isTrue();
    }

    @Test
    void authenticate_shouldDenyServiceAccountWithWrongPassword() {
        assertThat(policy.authenticate("materin-svc-consumer", "wrong")).isFalse();
    }

    @Test
    void authenticate_shouldDenyNullInput() {
        assertThat(policy.authenticate(null, "x")).isFalse();
        assertThat(policy.authenticate("u", null)).isFalse();
    }

    @Test
    void authorize_shouldAllowServiceAccountAllTopics() {
        assertThat(policy.authorize("materin-svc-bridge", "materin/+/+/report", "subscribe")).isTrue();
        assertThat(policy.authorize("materin-svc-bridge", "open/#", "subscribe")).isTrue();
    }

    @Test
    void authorize_shouldDenyDeviceCrossNamespace() {
        // 设备访问他人 namespace：policy 内 lookup 为空 → deny（依赖 lookup 的分支由 e2e 兜底）
        assertThat(policy.authorize("th-001", "materin/pk/other-device/report", "publish")).isFalse();
    }

    @Test
    void authorize_shouldDenyOpenNamespaceForDevice() {
        assertThat(policy.authorize("th-001", "open/anything", "publish")).isFalse();
    }

    @Test
    void authorize_shouldDenyNullTopic() {
        assertThat(policy.authorize("materin-svc-x", null, "publish")).isFalse();
    }
}
