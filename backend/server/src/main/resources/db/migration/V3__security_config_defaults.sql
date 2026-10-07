-- V3: 等保三级安全参数默认值（幂等：config_key 唯一键 + INSERT IGNORE，代码侧亦有默认值兜底）

INSERT IGNORE INTO sys_config (config_key, config_value, remark) VALUES
    ('security.password.min-length', '8', '密码最小长度'),
    ('security.password.require-uppercase', 'true', '密码需包含大写字母'),
    ('security.password.require-lowercase', 'true', '密码需包含小写字母'),
    ('security.password.require-digit', 'true', '密码需包含数字'),
    ('security.password.require-special', 'true', '密码需包含特殊字符'),
    ('security.password.expire-days', '90', '密码有效期（天），到期强制修改'),
    ('security.password.remind-days', '7', '密码到期前提醒天数'),
    ('security.lockout.max-fails', '5', '连续登录失败锁定阈值'),
    ('security.lockout.duration-minutes', '10', '登录锁定时长（分钟）');
