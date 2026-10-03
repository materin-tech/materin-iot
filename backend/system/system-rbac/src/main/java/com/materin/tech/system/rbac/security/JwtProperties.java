package com.materin.tech.system.rbac.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT 配置。 */
@Data
@ConfigurationProperties(prefix = "materin.security.jwt")
public class JwtProperties {

    /** HMAC 密钥，生产必须通过环境变量覆盖 */
    private String secret = "dev-only-materin-default-secret-change-me";

    /** accessToken 有效期（秒） */
    private long accessExpireSeconds = 2 * 60 * 60;

    /** refreshToken 有效期（秒） */
    private long refreshExpireSeconds = 7L * 24 * 60 * 60;
}
