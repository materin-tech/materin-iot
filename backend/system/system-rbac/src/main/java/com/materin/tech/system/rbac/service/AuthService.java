package com.materin.tech.system.rbac.service;

import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.rbac.dto.AuthDtos.ChangePasswordRequest;
import com.materin.tech.system.rbac.dto.AuthDtos.LoginRequest;
import com.materin.tech.system.rbac.dto.AuthDtos.LoginResponse;
import com.materin.tech.system.rbac.dto.AuthDtos.UserInfoResponse;
import com.materin.tech.system.rbac.entity.SysMenu;
import com.materin.tech.system.rbac.entity.SysRole;
import com.materin.tech.system.rbac.entity.SysUser;
import com.materin.tech.system.rbac.mapper.SysMenuMapper;
import com.materin.tech.system.rbac.mapper.SysRoleMapper;
import com.materin.tech.system.rbac.mapper.SysRoleMenuMapper;
import com.materin.tech.system.rbac.mapper.SysUserMapper;
import com.materin.tech.system.rbac.mapper.SysUserRoleMapper;
import com.materin.tech.system.rbac.security.CurrentUser;
import com.materin.tech.system.rbac.security.JwtTokenService;
import com.materin.tech.system.rbac.service.SecurityPolicyService.PasswordStatus;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/** 认证与当前用户信息。 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final JwtTokenService jwtTokenService;
    private final PasswordPolicy passwordPolicy;
    private final SecurityPolicyService securityPolicy;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOneByQuery(
                QueryWrapper.create().eq("username", request.username()));
        if (user == null || !encoder.matches(request.password(), user.getPassword())) {
            throw BizException.badRequest("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.badRequest("用户已被禁用");
        }
        List<String> roles = loadRoleNames(user.getId());
        String accessToken = jwtTokenService.createAccessToken(user.getId(), user.getUsername(), roles);
        return new LoginResponse(user.getId(), user.getUsername(), user.getNickname(), roles, accessToken,
                passwordStatusName(user.getPasswordUpdateTime()));
    }

    /** 当前用户修改本人密码（等保三级：验证原密码 + 新密码复杂度）。 */
    public void changeOwnPassword(CurrentUser current, ChangePasswordRequest request) {
        SysUser user = sysUserMapper.selectOneById(current.userId());
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (!encoder.matches(request.oldPassword(), user.getPassword())) {
            throw BizException.badRequest("原密码错误");
        }
        if (encoder.matches(request.newPassword(), user.getPassword())) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }
        passwordPolicy.validate(request.newPassword());
        user.setPassword(encoder.encode(request.newPassword()));
        user.setPasswordUpdateTime(java.time.LocalDateTime.now());
        sysUserMapper.update(user);
    }

    private String passwordStatusName(java.time.LocalDateTime passwordUpdateTime) {
        PasswordStatus status = securityPolicy.passwordStatus(passwordUpdateTime);
        return status.name();
    }

    public UserInfoResponse getUserInfo(CurrentUser current) {
        SysUser user = sysUserMapper.selectOneById(current.userId());
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return new UserInfoResponse(user.getId(), user.getUsername(), user.getNickname(),
                null, null, null, loadRoleNames(user.getId()));
    }

    /** 当前用户的权限码：角色 -> 角色菜单 -> 菜单 authCode 去重。 */
    public List<String> getAccessCodes(CurrentUser current) {
        List<Long> roleIds = sysUserRoleMapper.selectListByQuery(
                        QueryWrapper.create().eq("user_id", current.userId()))
                .stream().map(ur -> ur.getRoleId()).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = sysRoleMenuMapper.selectListByQuery(
                        QueryWrapper.create().in("role_id", roleIds))
                .stream().map(rm -> rm.getMenuId()).toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return sysMenuMapper.selectListByIds(menuIds).stream()
                .map(SysMenu::getAuthCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
    }

    List<String> loadRoleNames(Long userId) {
        List<Long> roleIds = sysUserRoleMapper.selectListByQuery(
                        QueryWrapper.create().eq("user_id", userId))
                .stream().map(ur -> ur.getRoleId()).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return sysRoleMapper.selectListByIds(roleIds).stream()
                .map(SysRole::getName).toList();
    }

    boolean matchesPassword(String raw, String encoded) {
        return encoder.matches(raw, encoded);
    }
}
