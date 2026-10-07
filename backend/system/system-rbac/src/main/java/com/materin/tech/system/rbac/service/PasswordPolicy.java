package com.materin.tech.system.rbac.service;

import com.materin.tech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 密码复杂度校验（等保三级）：规则来自 sys_config，可由管理员调整。 */
@Service
@RequiredArgsConstructor
public class PasswordPolicy {

    private final SecurityPolicyService policy;

    /** 校验密码复杂度，不合规时抛出包含全部未满足规则的 BizException。 */
    public void validate(String password) {
        if (!StringUtils.hasText(password)) {
            throw BizException.badRequest("密码不能为空");
        }
        List<String> violations = violations(password);
        if (!violations.isEmpty()) {
            throw BizException.badRequest("密码复杂度不满足要求：" + String.join("；", violations));
        }
    }

    public List<String> violations(String password) {
        List<String> violations = new ArrayList<>();
        int minLength = policy.config.getInt(
                SecurityPolicyService.KEY_PASSWORD_MIN_LENGTH, 8);
        if (password.length() < minLength) {
            violations.add("长度至少 " + minLength + " 位");
        }
        if (policy.config.getBool(SecurityPolicyService.KEY_PASSWORD_REQUIRE_UPPERCASE, true)
                && password.chars().noneMatch(Character::isUpperCase)) {
            violations.add("需包含大写字母");
        }
        if (policy.config.getBool(SecurityPolicyService.KEY_PASSWORD_REQUIRE_LOWERCASE, true)
                && password.chars().noneMatch(Character::isLowerCase)) {
            violations.add("需包含小写字母");
        }
        if (policy.config.getBool(SecurityPolicyService.KEY_PASSWORD_REQUIRE_DIGIT, true)
                && password.chars().noneMatch(Character::isDigit)) {
            violations.add("需包含数字");
        }
        if (policy.config.getBool(SecurityPolicyService.KEY_PASSWORD_REQUIRE_SPECIAL, true)
                && password.chars().noneMatch(c -> "!@#$%^&*()_+-=[]{}|;:,.<>?/~".indexOf(c) >= 0)) {
            violations.add("需包含特殊字符");
        }
        return violations;
    }
}
