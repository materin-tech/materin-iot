package com.materin.tech.system.rbac.service;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.rbac.dto.SystemDtos.UserItem;
import com.materin.tech.system.rbac.dto.SystemDtos.UserUpsertRequest;
import com.materin.tech.system.rbac.entity.SysUser;
import com.materin.tech.system.rbac.mapper.SysUserMapper;
import com.materin.tech.system.rbac.security.SensitiveDataCipher;
import com.materin.tech.system.rbac.security.TokenStateService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** 系统用户管理：对齐前端 /system/user 接口契约（等保三级：密码复杂度、实名制、解锁）。 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ID_TYPES = Set.of("ID_CARD", "PASSPORT", "OTHER");

    private final SysUserMapper sysUserMapper;
    private final PasswordPolicy passwordPolicy;
    private final SensitiveDataCipher cipher;
    private final TokenStateService tokenState;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PageResult<UserItem> list(long page, long pageSize, String name, String remark,
                                     Integer status, Long orgId, String startTime, String endTime) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StringUtils.hasText(name)) {
            String like = "%" + name + "%";
            wrapper.and(new QueryColumn("username").like(like)
                    .or(new QueryColumn("nickname").like(like)));
        }
        if (StringUtils.hasText(remark)) {
            wrapper.like("remark", remark);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (orgId != null) {
            wrapper.eq("org_id", orgId);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge("create_time", LocalDateTime.parse(startTime, TS));
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le("create_time", LocalDateTime.parse(endTime, TS));
        }
        Page<SysUser> result = sysUserMapper.paginate(Page.of(page, pageSize), wrapper);
        List<UserItem> items = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(items, result.getTotalRow());
    }

    public UserItem create(UserUpsertRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw BizException.badRequest("用户名不能为空");
        }
        String username = request.name().trim();
        if (sysUserMapper.selectCountByQuery(QueryWrapper.create().eq("username", username)) > 0) {
            throw BizException.badRequest("用户名已存在: " + username);
        }
        // 等保三级：创建用户必须设置符合复杂度要求的密码（不再使用弱默认密码）
        if (!StringUtils.hasText(request.password())) {
            throw BizException.badRequest("必须为新建用户设置密码");
        }
        passwordPolicy.validate(request.password());
        validateRealNameFields(request.phone(), request.idType());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setNickname(request.name());
        user.setPassword(encoder.encode(request.password()));
        user.setPasswordUpdateTime(LocalDateTime.now());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setOrgId(request.orgId());
        user.setRemark(request.remark());
        applyRealNameFields(user, request.phone(), request.idType(), request.idNo());
        sysUserMapper.insert(user);
        return toItem(user);
    }

    public UserItem update(Long id, UserUpsertRequest request) {
        SysUser user = requireUser(id);
        if (StringUtils.hasText(request.name())) {
            user.setNickname(request.name());
        }
        if (StringUtils.hasText(request.password())) {
            passwordPolicy.validate(request.password());
            user.setPassword(encoder.encode(request.password()));
            user.setPasswordUpdateTime(LocalDateTime.now());
        }
        user.setStatus(request.status() == null ? user.getStatus() : request.status());
        user.setOrgId(request.orgId() == null ? user.getOrgId() : request.orgId());
        user.setRemark(request.remark());
        if (request.phone() != null || request.idType() != null || request.idNo() != null) {
            validateRealNameFields(
                    request.phone() != null ? request.phone() : user.getPhone(),
                    request.idType() != null ? request.idType() : user.getIdType());
            applyRealNameFields(user,
                    request.phone() != null ? request.phone() : user.getPhone(),
                    request.idType() != null ? request.idType() : user.getIdType(),
                    StringUtils.hasText(request.idNo()) ? request.idNo() : null);
        }
        sysUserMapper.update(user);
        return toItem(user);
    }

    public void delete(Long id) {
        requireUser(id);
        sysUserMapper.deleteById(id);
    }

    /** 管理员重置用户密码（等保三级：密码可由管理员强制重置）。 */
    public void resetPassword(Long id, String newPassword) {
        passwordPolicy.validate(newPassword);
        SysUser user = requireUser(id);
        user.setPassword(encoder.encode(newPassword));
        user.setPasswordUpdateTime(LocalDateTime.now());
        sysUserMapper.update(user);
        // 密码重置后清空登录失败计数，避免旧锁定影响新密码登录
        tokenState.unlockLogin(user.getUsername());
    }

    /** 管理员解锁账号（清空 Redis 登录失败计数）。 */
    public void unlock(Long id) {
        SysUser user = requireUser(id);
        tokenState.unlockLogin(user.getUsername());
    }

    private SysUser requireUser(Long id) {
        SysUser user = sysUserMapper.selectOneById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在: " + id);
        }
        return user;
    }

    private void validateRealNameFields(String phone, String idType) {
        if (StringUtils.hasText(phone) && !phone.matches("^1[3-9]\\d{9}$")) {
            throw BizException.badRequest("手机号格式不正确（需为 11 位大陆手机号）");
        }
        if (StringUtils.hasText(idType) && !ID_TYPES.contains(idType)) {
            throw BizException.badRequest("证件类型不合法: " + idType);
        }
    }

    private void applyRealNameFields(SysUser user, String phone, String idType, String idNo) {
        user.setPhone(StringUtils.hasText(phone) ? phone : null);
        user.setIdType(StringUtils.hasText(idType) ? idType : null);
        // 证件号只允许与证件类型同时出现，AES 加密后落库
        user.setIdNo(StringUtils.hasText(idNo) && StringUtils.hasText(idType)
                ? cipher.encrypt(idNo) : user.getIdNo());
    }

    private UserItem toItem(SysUser user) {
        String name = user.getNickname() == null ? user.getUsername() : user.getNickname();
        String createTime = user.getCreateTime() == null ? null : user.getCreateTime().format(TS);
        String passwordUpdateTime = user.getPasswordUpdateTime() == null
                ? null : user.getPasswordUpdateTime().format(TS);
        String idNoMasked = null;
        if (StringUtils.hasText(user.getIdNo())) {
            String plain = cipher.decrypt(user.getIdNo());
            idNoMasked = "PASSPORT".equals(user.getIdType())
                    ? SensitiveDataCipher.mask(plain, 2, 2)
                    : SensitiveDataCipher.mask(plain, 3, 4);
        }
        return new UserItem(user.getId(), name, user.getStatus(), user.getOrgId(),
                user.getRemark(), user.getPhone(), user.getIdType(), idNoMasked,
                passwordUpdateTime, createTime);
    }
}
