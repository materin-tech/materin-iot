/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.tech.system.rbac.controller;

import com.materin.tech.common.core.ApiVersions;
import com.materin.tech.common.core.R;
import com.materin.tech.system.rbac.service.SecurityPolicyService;
import com.materin.tech.system.rbac.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 等保三级安全参数：/api/v1/system/config/security（存 sys_config，改动即时生效）。 */
@Tag(name = "安全设置")
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;
    private final SecurityPolicyService securityPolicy;

    @Operation(summary = "查询安全参数", description = "权限：需登录，功能权限码 AC_100130（安全设置）")
    @GetMapping("/security")
    public R<SecurityConfigView> get() {
        return R.ok(view());
    }

    @Operation(summary = "更新安全参数", description = "权限：需登录，功能权限码 AC_100130（安全设置）")
    @PutMapping("/security")
    public R<SecurityConfigView> update(@RequestBody SecurityConfigView request) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(SecurityPolicyService.KEY_PASSWORD_MIN_LENGTH,
                String.valueOf(request.getPasswordMinLength()));
        values.put(SecurityPolicyService.KEY_PASSWORD_REQUIRE_UPPERCASE,
                String.valueOf(request.isPasswordRequireUppercase()));
        values.put(SecurityPolicyService.KEY_PASSWORD_REQUIRE_LOWERCASE,
                String.valueOf(request.isPasswordRequireLowercase()));
        values.put(SecurityPolicyService.KEY_PASSWORD_REQUIRE_DIGIT,
                String.valueOf(request.isPasswordRequireDigit()));
        values.put(SecurityPolicyService.KEY_PASSWORD_REQUIRE_SPECIAL,
                String.valueOf(request.isPasswordRequireSpecial()));
        values.put(SecurityPolicyService.KEY_PASSWORD_EXPIRE_DAYS,
                String.valueOf(request.getPasswordExpireDays()));
        values.put(SecurityPolicyService.KEY_PASSWORD_REMIND_DAYS,
                String.valueOf(request.getPasswordRemindDays()));
        values.put(SecurityPolicyService.KEY_LOCKOUT_MAX_FAILS,
                String.valueOf(request.getLockoutMaxFails()));
        values.put(SecurityPolicyService.KEY_LOCKOUT_DURATION_MINUTES,
                String.valueOf(request.getLockoutDurationMinutes()));
        sysConfigService.upsert(values);
        return R.ok(view());
    }

    private SecurityConfigView view() {
        SecurityConfigView view = new SecurityConfigView();
        view.setPasswordMinLength(sysConfigService.getInt(
                SecurityPolicyService.KEY_PASSWORD_MIN_LENGTH, 8));
        view.setPasswordRequireUppercase(sysConfigService.getBool(
                SecurityPolicyService.KEY_PASSWORD_REQUIRE_UPPERCASE, true));
        view.setPasswordRequireLowercase(sysConfigService.getBool(
                SecurityPolicyService.KEY_PASSWORD_REQUIRE_LOWERCASE, true));
        view.setPasswordRequireDigit(sysConfigService.getBool(
                SecurityPolicyService.KEY_PASSWORD_REQUIRE_DIGIT, true));
        view.setPasswordRequireSpecial(sysConfigService.getBool(
                SecurityPolicyService.KEY_PASSWORD_REQUIRE_SPECIAL, true));
        view.setPasswordExpireDays(securityPolicy.passwordExpireDays());
        view.setPasswordRemindDays(securityPolicy.passwordRemindDays());
        view.setLockoutMaxFails(securityPolicy.lockoutMaxFails());
        view.setLockoutDurationMinutes(securityPolicy.lockoutDurationMinutes());
        return view;
    }

    /** 安全参数视图（前端安全设置页表单模型）。 */
    @Data
    public static class SecurityConfigView {
        private int passwordMinLength;
        private boolean passwordRequireUppercase;
        private boolean passwordRequireLowercase;
        private boolean passwordRequireDigit;
        private boolean passwordRequireSpecial;
        private int passwordExpireDays;
        private int passwordRemindDays;
        private int lockoutMaxFails;
        private int lockoutDurationMinutes;
    }
}
