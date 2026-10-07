-- V8: 通用 OTA 升级（固件包经 storage 中间件存对象存储，表只存元数据）

CREATE TABLE IF NOT EXISTS ota_package (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    product_id   BIGINT       NOT NULL,
    product_name VARCHAR(128) NOT NULL DEFAULT '',
    type         VARCHAR(16)  NOT NULL DEFAULT 'firmware' COMMENT 'firmware/software/config',
    module       VARCHAR(64)  NULL,
    version      VARCHAR(32)  NOT NULL,
    size         BIGINT       NULL,
    sign_method  VARCHAR(16)  NOT NULL DEFAULT 'md5',
    sign_value   VARCHAR(128) NULL,
    storage_key  VARCHAR(255) NULL COMMENT '对象存储 key（StorageClient 抽象）',
    content_type VARCHAR(64)  NULL,
    description  VARCHAR(255) NULL,
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '0-草稿 1-已发布',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OTA 固件包';

CREATE TABLE IF NOT EXISTS ota_task (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    package_id   BIGINT       NOT NULL,
    task_name    VARCHAR(128) NOT NULL DEFAULT '',
    device_count INT          NOT NULL DEFAULT 0,
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0-进行中 1-已完成 2-已取消',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OTA 升级任务';

CREATE TABLE IF NOT EXISTS ota_task_device (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT       NOT NULL,
    package_id  BIGINT       NOT NULL,
    device_id   BIGINT       NOT NULL,
    device_key  VARCHAR(64)  NOT NULL DEFAULT '',
    device_name VARCHAR(128) NOT NULL DEFAULT '',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待推送 1 已推送 2 下载中 3 升级中 4 成功 5 失败',
    progress    INT          NOT NULL DEFAULT 0,
    message     VARCHAR(512) NULL,
    push_count  INT          NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OTA 任务设备明细';
