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

    // ===== open broker（EMQX-2，第三方对接）专用判定 =====

    @Test
    void authenticateOpen_shouldAllowServiceAccountWithMatchingPassword() {
        assertThat(policy.authenticateOpen("materin-svc-emqx2-bridge", "materin-svc-dev-password")).isTrue();
    }

    @Test
    void authenticateOpen_shouldDenyServiceAccountWithWrongPassword() {
        assertThat(policy.authenticateOpen("materin-svc-emqx2-bridge", "wrong")).isFalse();
    }

    @Test
    void authenticateOpen_shouldAllowDeveloperAppKey() {
        OpenAppAuthChecker checker = Mockito.mock(OpenAppAuthChecker.class);
        Mockito.when(checker.existsByAppKey("ak-demo")).thenReturn(true);
        Mockito.when(checker.matches("ak-demo", "sk-demo")).thenReturn(true);
        Mockito.when(appProvider.getIfAvailable()).thenReturn(checker);
        assertThat(policy.authenticateOpen("ak-demo", "sk-demo")).isTrue();
    }

    @Test
    void authenticateOpen_shouldDenyDeviceCredential() {
        // 设备凭证不允许连开发者 broker：即使 lookup 命中也要拒绝
        DeviceCredentialLookup lookup = Mockito.mock(DeviceCredentialLookup.class);
        Mockito.when(lookup.findByKey("th-001"))
                .thenReturn(Optional.of(new DeviceCredentialLookup.DeviceCredential("th-001", 1L, "secret")));
        Mockito.when(credentialLookup.getIfAvailable()).thenReturn(lookup);
        assertThat(policy.authenticateOpen("th-001", "secret")).isFalse();
    }

    @Test
    void authenticateOpen_shouldDenyNullInput() {
        assertThat(policy.authenticateOpen(null, "x")).isFalse();
        assertThat(policy.authenticateOpen("u", null)).isFalse();
    }

    @Test
    void authorizeOpen_shouldAllowServiceAccountAllTopics() {
        assertThat(policy.authorizeOpen("materin-svc-emqx2-bridge", "open/materin/pk/dk/cmd", "publish")).isTrue();
        assertThat(policy.authorizeOpen("materin-svc-emqx2-bridge", "open/#", "subscribe")).isTrue();
    }

    @Test
    void authorizeOpen_shouldAllowAppKeyOnlyOpenNamespace() {
        OpenAppAuthChecker checker = Mockito.mock(OpenAppAuthChecker.class);
        Mockito.when(checker.existsByAppKey("ak-demo")).thenReturn(true);
        Mockito.when(appProvider.getIfAvailable()).thenReturn(checker);
        assertThat(policy.authorizeOpen("ak-demo", "open/materin/pk/dk/report", "subscribe")).isTrue();
        assertThat(policy.authorizeOpen("ak-demo", "materin/pk/dk/report", "publish")).isFalse();
    }

    @Test
    void authorizeOpen_shouldDenyUnknownUser() {
        Mockito.when(appProvider.getIfAvailable()).thenReturn(null);
        // 非 open/ 主题 + 非服务账号（含设备凭证）一律拒绝
        assertThat(policy.authorizeOpen("th-001", "materin/pk/th-001/report", "publish")).isFalse();
    }

    @Test
    void authorizeOpen_shouldDenyNullInput() {
        assertThat(policy.authorizeOpen("u", null, "publish")).isFalse();
        assertThat(policy.authorizeOpen(null, "open/#", "subscribe")).isFalse();
    }
}
