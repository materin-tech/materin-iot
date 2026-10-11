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
package com.materin.tech.system.rbac.service;

import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.rbac.service.SecurityPolicyService.PasswordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityPolicyTest {

    private SysConfigService config;
    private SecurityPolicyService policy;
    private PasswordPolicy passwordPolicy;

    @BeforeEach
    void setUp() {
        config = new SysConfigService(null) {
            // 测试用：跳过数据库，直接读写内存默认值
            @Override
            public String get(String key, String defaultValue) {
                return defaultValue;
            }

            @Override
            public int getInt(String key, int defaultValue) {
                return defaultValue;
            }

            @Override
            public boolean getBool(String key, boolean defaultValue) {
                return defaultValue;
            }
        };
        policy = new SecurityPolicyService(config);
        passwordPolicy = new PasswordPolicy(policy);
    }

    @Test
    @DisplayName("符合复杂度要求的密码通过校验")
    void validate_acceptsStrongPassword() {
        assertThatCode(() -> passwordPolicy.validate("Abc@1234")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("空密码被拒绝")
    void validate_rejectsBlank() {
        assertThatThrownBy(() -> passwordPolicy.validate(" "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码不能为空");
    }

    @Test
    @DisplayName("弱密码报告全部未满足的规则")
    void validate_listsAllViolations() {
        assertThatThrownBy(() -> passwordPolicy.validate("abc"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("长度至少 8 位")
                .hasMessageContaining("大写字母")
                .hasMessageContaining("数字")
                .hasMessageContaining("特殊字符");
    }

    @Test
    @DisplayName("密码状态：从未设置视为已过期")
    void passwordStatus_nullIsExpired() {
        assertThat(policy.passwordStatus(null)).isEqualTo(PasswordStatus.EXPIRED);
    }

    @Test
    @DisplayName("密码状态：临近有效期进入提醒区间")
    void passwordStatus_expiring() {
        // 默认有效期 90 天、提醒 7 天：83 天前 = 提醒期
        LocalDateTime updateTime = LocalDateTime.now().minusDays(83);
        assertThat(policy.passwordStatus(updateTime)).isEqualTo(PasswordStatus.EXPIRING);
    }

    @Test
    @DisplayName("密码状态：刚修改为正常")
    void passwordStatus_normal() {
        assertThat(policy.passwordStatus(LocalDateTime.now().minusDays(1)))
                .isEqualTo(PasswordStatus.NORMAL);
    }

    @Test
    @DisplayName("锁定参数来自配置默认值")
    void lockout_defaults() {
        assertThat(policy.lockoutMaxFails()).isEqualTo(5);
        assertThat(policy.lockoutDurationMinutes()).isEqualTo(10);
    }
}
