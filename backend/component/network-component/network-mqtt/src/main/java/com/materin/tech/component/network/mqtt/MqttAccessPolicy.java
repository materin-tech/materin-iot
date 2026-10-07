package com.materin.tech.component.network.mqtt;

import com.materin.tech.common.mqtt.MqttTopics;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.OpenAppAuthChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * MQTT 接入判定核心策略（broker 无关，与协议适配层 {@link MqttAuthController} 分离），
 * 供 EMQX authenticator/authorizer HTTP 回调委托调用，便于将来更换 broker 时复用。
 * 三分流顺序：服务账号（materin-svc-*）→ 开发者 AK/SK（open/ 命名空间）→ 设备（自身 namespace）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttAccessPolicy {

    /** 服务账号统一前缀（平台 MQTT 消费端） */
    static final String SERVICE_PREFIX = "materin-svc-";

    private final MqttProperties properties;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final ObjectProvider<OpenAppAuthChecker> appServiceProvider;

    /** 认证：username/password 合法返回 true。 */
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        if (username.startsWith(SERVICE_PREFIX)) {
            return properties.getServicePassword().equals(password);
        }
        OpenAppAuthChecker appChecker = appServiceProvider.getIfAvailable();
        if (appChecker != null && appChecker.existsByAppKey(username)) {
            boolean ok = appChecker.matches(username, password);
            if (!ok) {
                log.warn("MQTT 开发者认证失败: appKey={}", username);
            }
            return ok;
        }
        DeviceCredentialLookup lookup = credentialLookup.getIfAvailable();
        if (lookup == null) {
            return false;
        }
        Optional<DeviceCredentialLookup.DeviceCredential> credential = lookup.findByKey(username);
        if (credential.isEmpty() || !credential.get().secret().equals(password)) {
            log.warn("MQTT 认证失败: deviceKey={}", username);
            return false;
        }
        return true;
    }

    /** 授权：action ∈ {publish, subscribe}。设备限自身 namespace，AK 限 open/，服务账号全放行。 */
    public boolean authorize(String username, String topic, String action) {
        if (username == null || topic == null) {
            return false;
        }
        // 服务账号放行全部（含通配符）
        if (username.startsWith(SERVICE_PREFIX)) {
            return true;
        }
        // 开发者应用：仅允许 open/ 命名空间
        OpenAppAuthChecker appChecker = appServiceProvider.getIfAvailable();
        if (appChecker != null && appChecker.existsByAppKey(username)) {
            return topic.startsWith("open/");
        }
        String[] parsed = MqttTopics.parse(topic);
        if (parsed == null) {
            return false;
        }
        String productKey = parsed[0];
        String deviceKey = parsed[1];
        String suffix = parsed[2];
        String rest = parsed[3];
        // 自身 namespace 校验：topic 中的 deviceKey 必须是本人，productKey 必须是本人所属产品
        if (!deviceKey.equals(username)) {
            return false;
        }
        Optional<String> ownProductKey = ownProductKey(username);
        if (ownProductKey.isEmpty() || !ownProductKey.get().equals(productKey)) {
            return false;
        }
        if ("publish".equals(action)) {
            return MqttTopics.isUpSuffix(suffix) && rest.isEmpty();
        }
        if ("subscribe".equals(action)) {
            return MqttTopics.isDownPrefix(suffix)
                    && (rest.isEmpty() || !rest.contains("#") || rest.endsWith("#"));
        }
        return false;
    }

    private Optional<String> ownProductKey(String deviceKey) {
        DeviceCredentialLookup lookup = credentialLookup.getIfAvailable();
        if (lookup == null) {
            return Optional.empty();
        }
        return lookup.findProductKeyByKey(deviceKey);
    }

    /** 便捷重载：EMQX 适配层直接透传 Map body。 */
    public boolean authenticate(Map<String, String> body) {
        return authenticate(body.get("username"), body.get("password"));
    }

    /** 便捷重载：EMQX 适配层直接透传 Map body。 */
    public boolean authorize(Map<String, String> body) {
        return authorize(body.get("username"), body.get("topic"), body.get("action"));
    }

    // ===== open broker（EMQX-2，第三方对接）专用判定：仅服务账号 + 开发者 AK/SK，设备凭证一律拒绝 =====

    /**
     * open broker 认证：username=AK（或服务账号）。
     * 与 {@link #authenticate} 的关键差异：不回落到设备凭证分支。
     */
    public boolean authenticateOpen(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        if (username.startsWith(SERVICE_PREFIX)) {
            return properties.getServicePassword().equals(password);
        }
        OpenAppAuthChecker appChecker = appServiceProvider.getIfAvailable();
        if (appChecker == null) {
            return false;
        }
        if (!appChecker.existsByAppKey(username)) {
            log.warn("MQTT open 认证失败（AK 不存在）: appKey={}", username);
            return false;
        }
        boolean ok = appChecker.matches(username, password);
        if (!ok) {
            log.warn("MQTT open 认证失败: appKey={}", username);
        }
        return ok;
    }

    /**
     * open broker 授权：服务账号全放行，开发者 AK 仅限 open/ 命名空间。
     * 与 {@link #authorize} 的关键差异：设备 namespace 分支不存在。
     */
    public boolean authorizeOpen(String username, String topic, String action) {
        if (username == null || topic == null) {
            return false;
        }
        if (username.startsWith(SERVICE_PREFIX)) {
            return true;
        }
        OpenAppAuthChecker appChecker = appServiceProvider.getIfAvailable();
        return appChecker != null && appChecker.existsByAppKey(username) && topic.startsWith("open/");
    }

    /** 便捷重载：EMQX 适配层直接透传 Map body。 */
    public boolean authenticateOpen(Map<String, String> body) {
        return authenticateOpen(body.get("username"), body.get("password"));
    }

    /** 便捷重载：EMQX 适配层直接透传 Map body。 */
    public boolean authorizeOpen(Map<String, String> body) {
        return authorizeOpen(body.get("username"), body.get("topic"), body.get("action"));
    }
}
