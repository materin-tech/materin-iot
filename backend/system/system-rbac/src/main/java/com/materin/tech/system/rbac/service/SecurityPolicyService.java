package com.materin.tech.system.rbac.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 等保三级安全策略：密码复杂度、密码有效期、登录锁定阈值。
 * 参数存 sys_config（管理员可在「安全设置」页调整），代码内默认值兜底。
 */
@Service
@RequiredArgsConstructor
public class SecurityPolicyService {

    public enum PasswordStatus { EXPIRED, EXPIRING, NORMAL }

    /** 参数键常量，前端安全设置页按同一份键展示 */
    public static final String KEY_PASSWORD_MIN_LENGTH = "security.password.min-length";
    public static final String KEY_PASSWORD_REQUIRE_UPPERCASE = "security.password.require-uppercase";
    public static final String KEY_PASSWORD_REQUIRE_LOWERCASE = "security.password.require-lowercase";
    public static final String KEY_PASSWORD_REQUIRE_DIGIT = "security.password.require-digit";
    public static final String KEY_PASSWORD_REQUIRE_SPECIAL = "security.password.require-special";
    public static final String KEY_PASSWORD_EXPIRE_DAYS = "security.password.expire-days";
    public static final String KEY_PASSWORD_REMIND_DAYS = "security.password.remind-days";
    public static final String KEY_LOCKOUT_MAX_FAILS = "security.lockout.max-fails";
    public static final String KEY_LOCKOUT_DURATION_MINUTES = "security.lockout.duration-minutes";

    final SysConfigService config; // 同包内 PasswordPolicy 复用

    // ---- 密码有效期 ----

    public int passwordExpireDays() {
        return config.getInt(KEY_PASSWORD_EXPIRE_DAYS, 90);
    }

    public int passwordRemindDays() {
        return config.getInt(KEY_PASSWORD_REMIND_DAYS, 7);
    }

    /** 按密码最后修改时间计算状态；时间为空表示从未设置（视作已过期，首次登录强制修改）。 */
    public PasswordStatus passwordStatus(LocalDateTime passwordUpdateTime) {
        if (passwordUpdateTime == null) {
            return PasswordStatus.EXPIRED;
        }
        long ageDays = ChronoUnit.DAYS.between(passwordUpdateTime, LocalDateTime.now());
        int expireDays = passwordExpireDays();
        if (ageDays >= expireDays) {
            return PasswordStatus.EXPIRED;
        }
        if (ageDays >= expireDays - passwordRemindDays()) {
            return PasswordStatus.EXPIRING;
        }
        return PasswordStatus.NORMAL;
    }

    // ---- 登录锁定 ----

    public int lockoutMaxFails() {
        return config.getInt(KEY_LOCKOUT_MAX_FAILS, 5);
    }

    public int lockoutDurationMinutes() {
        return config.getInt(KEY_LOCKOUT_DURATION_MINUTES, 10);
    }
}
