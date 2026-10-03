package com.materin.tech.system.rbac.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** JWT 签发与校验（jjwt HMAC）。 */
@Service
public class JwtTokenService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username, List<String> roles) {
        return build(userId, username, roles, TYPE_ACCESS, properties.getAccessExpireSeconds());
    }

    /** 校验并解析 accessToken，返回用户上下文（含 jti）；无效/过期抛 JwtException。 */
    @SuppressWarnings("unchecked")
    public CurrentUser parseAccessToken(String token) {
        Claims claims = parse(token, TYPE_ACCESS);
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        return new CurrentUser(Long.valueOf(claims.getSubject()), claims.get(CLAIM_USERNAME, String.class),
                roles == null ? List.of() : roles, claims.getId());
    }

    /** 读取 token 的 jti。 */
    public String jtiOf(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getId();
    }

    /** 读取 token 剩余有效期（秒）。 */
    public long remainingSeconds(String token) {
        Date expiration = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getExpiration();
        return Math.max(0, (expiration.getTime() - System.currentTimeMillis()) / 1000);
    }

    public long getRefreshExpireSeconds() {
        return properties.getRefreshExpireSeconds();
    }

    private String build(Long userId, String username, List<String> roles, String type, long expireSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TYPE, type)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(key)
                .compact();
    }

    private Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("token type mismatch");
        }
        return claims;
    }
}
