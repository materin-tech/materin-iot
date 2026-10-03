package com.materin.tech.system.rbac.service;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.rbac.dto.SystemDtos.UserItem;
import com.materin.tech.system.rbac.dto.SystemDtos.UserUpsertRequest;
import com.materin.tech.system.rbac.entity.SysUser;
import com.materin.tech.system.rbac.mapper.SysUserMapper;
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

/** 系统用户管理：对齐前端 /system/user 接口契约。 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 前端用户表单暂无密码字段，创建用户时使用该默认密码 */
    static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper sysUserMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PageResult<UserItem> list(long page, long pageSize, String name, String remark,
                                     Integer status, Long deptId, String startTime, String endTime) {
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
        if (deptId != null) {
            wrapper.eq("dept_id", deptId);
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
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setNickname(request.name());
        user.setPassword(encoder.encode(DEFAULT_PASSWORD));
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setDeptId(request.deptId());
        user.setRemark(request.remark());
        sysUserMapper.insert(user);
        return toItem(user);
    }

    public UserItem update(Long id, UserUpsertRequest request) {
        SysUser user = requireUser(id);
        if (StringUtils.hasText(request.name())) {
            user.setNickname(request.name());
        }
        user.setStatus(request.status() == null ? user.getStatus() : request.status());
        user.setDeptId(request.deptId() == null ? user.getDeptId() : request.deptId());
        user.setRemark(request.remark());
        sysUserMapper.update(user);
        return toItem(user);
    }

    public void delete(Long id) {
        requireUser(id);
        sysUserMapper.deleteById(id);
    }

    private SysUser requireUser(Long id) {
        SysUser user = sysUserMapper.selectOneById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在: " + id);
        }
        return user;
    }

    private UserItem toItem(SysUser user) {
        String name = user.getNickname() == null ? user.getUsername() : user.getNickname();
        String createTime = user.getCreateTime() == null ? null : user.getCreateTime().format(TS);
        return new UserItem(user.getId(), name, user.getStatus(), user.getDeptId(),
                user.getRemark(), createTime);
    }
}
