package com.materin.tech.component.network.mqtt;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * EMQX HTTP 认证/授权回调（EMQX authenticator / authorizer 调用）。
 * 返回 {"result": "allow"|"deny"} —— 判定逻辑在 {@link MqttAccessPolicy}（策略与协议适配分离）。
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class MqttAuthController {

    private final MqttAccessPolicy policy;

    /** EMQX password_based HTTP 认证：username=deviceKey（或服务账号），password=secret。 */
    @PostMapping("/mqtt/auth")
    public Map<String, String> auth(@RequestBody Map<String, String> body) {
        return Map.of("result", policy.authenticate(body) ? "allow" : "deny");
    }

    /** EMQX HTTP 授权：设备只能访问自己的 namespace。 */
    @PostMapping("/mqtt/acl")
    public Map<String, String> acl(@RequestBody Map<String, String> body) {
        return Map.of("result", policy.authorize(body) ? "allow" : "deny");
    }
}
