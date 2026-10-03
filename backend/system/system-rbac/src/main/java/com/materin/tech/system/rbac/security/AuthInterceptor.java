package com.materin.tech.system.rbac.security;

import com.materin.tech.common.core.ApiVersions;
import com.materin.tech.common.core.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 登录态拦截：校验 Bearer accessToken，解析出 CurrentUser 放入请求属性。 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_CURRENT_USER = "materin.currentUser";

    private final JwtTokenService jwtTokenService;
    private final TokenStateService tokenStateService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return reject(response);
        }
        try {
            CurrentUser user = jwtTokenService.parseAccessToken(authorization.substring(7));
            if (user.jti() != null && tokenStateService.isAccessTokenRevoked(user.jti())) {
                return reject(response);
            }
            request.setAttribute(ATTR_CURRENT_USER, user);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return reject(response);
        }
    }

    private boolean reject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(401, "未登录或登录已过期")));
        return false;
    }
}
