-- V12: 文件管理（全平台经 StorageClient 上传的文件统一登记）+ 系统管理菜单

CREATE TABLE IF NOT EXISTS sys_file (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name    VARCHAR(255) NOT NULL COMMENT '原始文件名',
    biz_type     VARCHAR(32)  NOT NULL DEFAULT 'generic' COMMENT '业务来源：ota/generic/...',
    biz_ref_id   BIGINT       NULL COMMENT '业务引用 ID（如 ota_package.id）',
    bucket       VARCHAR(128) NOT NULL DEFAULT '',
    storage_key  VARCHAR(255) NOT NULL COMMENT '对象存储 key（StorageClient 抽象）',
    content_type VARCHAR(64)  NULL,
    size         BIGINT       NOT NULL DEFAULT 0,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_sys_file_biz (biz_type, biz_ref_id),
    KEY idx_sys_file_name (file_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统文件登记';

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '文件管理', (SELECT id FROM sys_menu WHERE path = '/system'), 'menu', '/system/files',
       'system/files', NULL, 1, 6,
       '{"name":"SystemFiles","title":"文件管理","icon":"lucide:folder-open","order":6}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/files');
