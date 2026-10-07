package com.materin.tech.system.rbac.entity;

import com.materin.tech.common.entity.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户（等保三级：实名手机号、证件号加密存储、密码期限跟踪）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    /** BCrypt 散列，接口层永不返回。 */
    private String password;

    private String nickname;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    /** 组织 ID（原部门） */
    private Long orgId;

    private String remark;

    /** 实名手机号 */
    private String phone;

    /** 证件类型：ID_CARD-身份证 PASSPORT-护照 OTHER-其他 */
    private String idType;

    /** 证件号（AES-GCM 加密存储，接口层只返回脱敏值） */
    private String idNo;

    /** 密码最后修改时间；空表示从未主动设置（首次登录需修改） */
    private LocalDateTime passwordUpdateTime;
}
