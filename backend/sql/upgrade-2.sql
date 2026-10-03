USE materin;

-- 设备表扩展
ALTER TABLE device
  ADD COLUMN product_id   BIGINT       NULL AFTER status,
  ADD COLUMN product_name VARCHAR(128) NULL AFTER product_id,
  ADD COLUMN firmware     VARCHAR(64)  NULL AFTER product_name,
  ADD COLUMN last_online  VARCHAR(64)  NULL AFTER firmware,
  ADD COLUMN remark       VARCHAR(255) NULL AFTER last_online;

-- 菜单表扩展
ALTER TABLE sys_menu
  ADD COLUMN component VARCHAR(128) NULL AFTER path,
  ADD COLUMN meta      TEXT         NULL AFTER sort;

-- 产品 / 物模型 / 告警 / 规则
CREATE TABLE IF NOT EXISTS product (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    product_key VARCHAR(64)  NOT NULL,
    category    VARCHAR(64)  NULL,
    protocol    VARCHAR(32)  NULL,
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-开发中 1-已发布',
    source      VARCHAR(16)  NOT NULL DEFAULT 'custom' COMMENT 'standard/custom',
    device_count INT         NOT NULL DEFAULT 0,
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_key (product_key)
) ENGINE = InnoDB COMMENT '产品';

CREATE TABLE IF NOT EXISTS thing_model (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    product_id      BIGINT       NOT NULL,
    product_name    VARCHAR(128) NULL,
    version         VARCHAR(32)  NOT NULL DEFAULT 'v1.0',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0-草稿 1-已发布',
    remark          VARCHAR(255) NULL,
    properties_json MEDIUMTEXT   NULL,
    methods_json    MEDIUMTEXT   NULL,
    events_json     MEDIUMTEXT   NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '物模型';

CREATE TABLE IF NOT EXISTS device_alert (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    device_id   BIGINT       NULL,
    device_name VARCHAR(128) NULL,
    level       VARCHAR(16)  NOT NULL DEFAULT 'info' COMMENT 'info/warn/error',
    content     VARCHAR(512) NULL,
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-未处理 1-已处理',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '设备告警';

CREATE TABLE IF NOT EXISTS device_rule (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    trigger     VARCHAR(512) NULL,
    action      VARCHAR(512) NULL,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0-停用 1-启用',
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '设备规则';
