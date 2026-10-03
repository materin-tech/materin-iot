package com.materin.tech.system.rbac.security;

import java.util.List;

/** 当前登录用户上下文（来自 accessToken 解析 + Redis 状态校验）。 */
public record CurrentUser(Long userId, String username, List<String> roles, String jti) {

    public CurrentUser(Long userId, String username, List<String> roles) {
        this(userId, username, roles, null);
    }
}
