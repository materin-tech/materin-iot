package com.materin.tech.system.rbac.controller;

import com.materin.tech.common.core.ApiVersions;
import com.materin.tech.common.core.R;
import com.materin.tech.system.rbac.dto.AuthDtos.ChangePasswordRequest;
import com.materin.tech.system.rbac.dto.AuthDtos.LoginRequest;
import com.materin.tech.system.rbac.dto.AuthDtos.LoginResponse;
import com.materin.tech.system.rbac.dto.AuthDtos.UserInfoResponse;
import com.materin.tech.system.rbac.security.AuthInterceptor;
import com.materin.tech.system.rbac.security.CurrentUser;
import com.materin.tech.system.rbac.security.JwtTokenService;
import com.materin.tech.system.rbac.security.TokenStateService;
import com.materin.tech.system.rbac.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 认证接口：登录 / 刷新 / 登出 / 权限码 / 当前用户（状态存 Redis，服务无状态）。 */
@Tag(name = "认证")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "materin_refresh_token";

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;
    private final TokenStateService tokenState;

    @Operation(summary = "登录并签发令牌", description = "权限：公开接口（无需登录）")
    @PostMapping("/auth/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        if (tokenState.isLoginLocked(request.username())) {
            throw new com.materin.tech.common.exception.BizException(429, "失败次数过多，账户暂时锁定");
        }
        LoginResponse login;
        try {
            login = authService.login(request);
        } catch (com.materin.tech.common.exception.BizException e) {
            tokenState.recordLoginFailure(request.username());
            throw e;
        }
        tokenState.clearLoginFailures(request.username());
        CurrentUser current = new CurrentUser(login.userId(), login.username(), login.roles());
        String refreshToken = tokenState.issueRefreshToken(current);
        Cookie cookie = new Cookie(REFRESH_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setPath(ApiVersions.V1_PREFIX + "/auth");
        cookie.setMaxAge((int) java.time.Duration.ofDays(7).toSeconds());
        response.addCookie(cookie);
        return R.ok(login);
    }

    /** 刷新 accessToken：读取 opaque refreshToken cookie（Redis 校验+轮换）。 */
    @Operation(summary = "刷新访问令牌", description = "权限：公开接口（凭 refreshToken Cookie）")
    @PostMapping("/auth/refresh")
    public R<String> refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieValue(request);
        if (token == null) {
            return R.fail(401, "缺少 refreshToken");
        }
        var consumed = tokenState.consumeRefreshToken(token);
        if (consumed.isEmpty()) {
            return R.fail(401, "refreshToken 已失效");
        }
        CurrentUser user = consumed.get();
        String accessToken = jwtTokenService.createAccessToken(user.userId(), user.username(), user.roles());
        // 轮换：签发新的 opaque refreshToken
        String newRefresh = tokenState.issueRefreshToken(user);
        Cookie cookie = new Cookie(REFRESH_COOKIE, newRefresh);
        cookie.setHttpOnly(true);
        cookie.setPath(ApiVersions.V1_PREFIX + "/auth");
        cookie.setMaxAge((int) java.time.Duration.ofDays(7).toSeconds());
        response.addCookie(cookie);
        return R.ok(accessToken);
    }

    @Operation(summary = "退出登录并吊销令牌", description = "权限：需登录")
    @PostMapping("/auth/logout")
    public R<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String accessToken = authorization.substring(7);
            try {
                tokenState.revokeAccessToken(jwtTokenService.jtiOf(accessToken),
                        jwtTokenService.remainingSeconds(accessToken));
            } catch (Exception ignored) {
            }
        }
        tokenState.revokeRefreshToken(cookieValue(request));
        Cookie cookie = new Cookie(REFRESH_COOKIE, null);
        cookie.setHttpOnly(true);
        cookie.setPath(ApiVersions.V1_PREFIX + "/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return R.ok();
    }

    @Operation(summary = "修改本人密码", description = "权限：需登录（等保三级：验证原密码，新密码满足复杂度）")
    @PostMapping("/user/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        authService.changeOwnPassword(current(httpRequest), request);
        return R.ok();
    }

    @Operation(summary = "获取当前用户权限码", description = "权限：需登录")
    @GetMapping("/auth/codes")
    public R<List<String>> codes(HttpServletRequest request) {
        return R.ok(authService.getAccessCodes(current(request)));
    }

    @Operation(summary = "获取当前用户信息", description = "权限：需登录")
    @GetMapping("/user/info")
    public R<UserInfoResponse> userInfo(HttpServletRequest request) {
        return R.ok(authService.getUserInfo(current(request)));
    }

    private CurrentUser current(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(AuthInterceptor.ATTR_CURRENT_USER);
    }

    private String cookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
