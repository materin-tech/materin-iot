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
package com.materin.tech.system.rbac.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** 认证相关 DTO。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(description = "登录请求")
    public record LoginRequest(
            @Schema(description = "登录名") @NotBlank String username,
            @Schema(description = "密码") @NotBlank String password) {
    }

    @Schema(description = "登录响应：用户信息 + 访问令牌")
    public record LoginResponse(
            @Schema(description = "用户 ID") Long userId,
            @Schema(description = "登录名") String username,
            @Schema(description = "姓名/昵称") String realName,
            @Schema(description = "角色名列表") List<String> roles,
            @Schema(description = "访问令牌（JWT），后续请求放 Authorization: Bearer 头") String accessToken,
            @Schema(description = "密码状态：NORMAL-正常 EXPIRING-即将到期 EXPIRED-已过期需修改") String passwordStatus) {
    }

    @Schema(description = "当前用户信息（对齐前端 vben UserInfo）")
    public record UserInfoResponse(
            @Schema(description = "用户 ID") Long userId,
            @Schema(description = "登录名") String username,
            @Schema(description = "姓名/昵称") String realName,
            @Schema(description = "头像地址（暂为空）") String avatar,
            @Schema(description = "个人描述（暂为空）") String desc,
            @Schema(description = "登录后首页路径（暂为空）") String homePath,
            @Schema(description = "角色名列表") List<String> roles) {
    }

    @Schema(description = "修改本人密码请求")
    public record ChangePasswordRequest(
            @Schema(description = "原密码") @NotBlank String oldPassword,
            @Schema(description = "新密码") @NotBlank String newPassword) {
    }
}
