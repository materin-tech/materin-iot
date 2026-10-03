package com.materin.tech.component.network.mqtt;

import com.materin.tech.common.mqtt.MqttTopics;
import io.swagger.v3.oas.annotations.Hidden;
import com.materin.tech.common.spi.DeviceCredentialLookup;
import com.materin.tech.common.spi.OpenAppAuthChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * EMQX HTTP 认证/授权回调（EMQX authenticator / authorizer 调用）。
 * 返回 {"result": "allow"|"deny"} —— 非法凭证与越权 topic 一律拒绝。
 */
@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
public class MqttAuthController {

    private final MqttProperties properties;
    private final ObjectProvider<DeviceCredentialLookup> credentialLookup;
    private final org.springframework.beans.factory.ObjectProvider<OpenAppAuthChecker> appServiceProvider;

    /** EMQX password_based HTTP 认证：username=deviceKey（或服务账号），password=secret。 */
    @PostMapping("/mqtt/auth")
    public Map<String, String> auth(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return Map.of("result", "deny");
        }
        // 服务账号（平台消费端 / 集成桥接）
        if (username.startsWith("materin-svc-")) {
            return Map.of("result", properties.getServicePassword().equals(password)
                    ? "allow" : "deny");
        }
        // 开发者应用 AK/SK（EMQX-2 第二套鉴权逻辑）
        OpenAppAuthChecker appChecker = appServiceProvider.getIfAvailable();
        if (appChecker != null && appChecker.existsByAppKey(username)) {
            boolean ok = appChecker.matches(username, password);
            if (!ok) {
                log.warn("MQTT 开发者认证失败: appKey={}", username);
            }
            return Map.of("result", ok ? "allow" : "deny");
        }
        // 设备账号
        DeviceCredentialLookup lookup = credentialLookup.getIfAvailable();
        if (lookup == null) {
            return Map.of("result", "deny");
        }
        Optional<DeviceCredentialLookup.DeviceCredential> credential = lookup.findByKey(username);
        if (credential.isEmpty() || !credential.get().secret().equals(password)) {
            log.warn("MQTT 认证失败: deviceKey={}", username);
            return Map.of("result", "deny");
        }
        return Map.of("result", "allow");
    }

    /**
     * EMQX HTTP 授权：设备只能访问自己的 namespace。
     * pub: materin/{ownPk}/{ownDk}/(report|event|reply)
     * sub: materin/{ownPk}/{ownDk}/cmd[/...]
     */
    @PostMapping("/mqtt/acl")
    public Map<String, String> acl(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String topic = body.get("topic");
        String action = body.get("action");
        if (username == null || topic == null) {
            return Map.of("result", "deny");
        }
        // 服务账号放行全部（含通配符）
        if (username.startsWith("materin-svc-")) {
            return Map.of("result", "allow");
        }
        // 开发者应用：仅允许 open/ 命名空间
        OpenAppAuthChecker appChecker = appServiceProvider.getIfAvailable();
        if (appChecker != null && appChecker.existsByAppKey(username)) {
            boolean ok = topic.startsWith("open/");
            return Map.of("result", ok ? "allow" : "deny");
        }
        String[] parsed = MqttTopics.parse(topic);
        if (parsed == null) {
            return Map.of("result", "deny");
        }
        String productKey = parsed[0];
        String deviceKey = parsed[1];
        String suffix = parsed[2];
        String rest = parsed[3];
        // 自身 namespace 校验：topic 中的 deviceKey 必须是本人，productKey 必须是本人所属产品
        if (!deviceKey.equals(username)) {
            return Map.of("result", "deny");
        }
        Optional<String> ownProductKey = ownProductKey(username);
        if (ownProductKey.isEmpty() || !ownProductKey.get().equals(productKey)) {
            return Map.of("result", "deny");
        }
        if ("publish".equals(action)) {
            boolean ok = MqttTopics.isUpSuffix(suffix) && rest.isEmpty();
            return Map.of("result", ok ? "allow" : "deny");
        }
        if ("subscribe".equals(action)) {
            boolean ok = MqttTopics.isDownPrefix(suffix)
                    && (rest.isEmpty() || !rest.contains("#") || rest.endsWith("#"));
            return Map.of("result", ok ? "allow" : "deny");
        }
        return Map.of("result", "deny");
    }

    private Optional<String> ownProductKey(String deviceKey) {
        DeviceCredentialLookup lookup = credentialLookup.getIfAvailable();
        if (lookup == null) {
            return Optional.empty();
        }
        return lookup.findProductKeyByKey(deviceKey);
    }
}
