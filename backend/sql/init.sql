-- 格物 IoT 平台初始化脚本
CREATE DATABASE IF NOT EXISTS materin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE materin;

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(128) NULL COMMENT 'BCrypt 散列',
    nickname    VARCHAR(64)  NULL,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
    dept_id     BIGINT       NULL,
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB COMMENT '系统用户';

CREATE TABLE IF NOT EXISTS device (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    device_key  VARCHAR(64)  NOT NULL COMMENT '设备唯一标识（协议层寻址）',
    name        VARCHAR(128) NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未激活 / 1 在线 / 2 离线',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_key (device_key)
) ENGINE = InnoDB COMMENT '设备台账';
USE materin;

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL COMMENT '角色名',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_name (name)
) ENGINE = InnoDB COMMENT '角色';

CREATE TABLE IF NOT EXISTS sys_dept (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL,
    pid         BIGINT       NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '部门';

CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL,
    pid         BIGINT       NULL,
    type        VARCHAR(16)  NOT NULL DEFAULT 'menu' COMMENT 'catalog/menu/button',
    path        VARCHAR(128) NULL,
    auth_code   VARCHAR(64)  NULL COMMENT '权限标识',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
    sort        INT          NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '菜单/权限点';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB COMMENT '用户-角色';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE = InnoDB COMMENT '角色-菜单';
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
    `trigger`   VARCHAR(512) NULL,
    action      VARCHAR(512) NULL,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0-停用 1-启用',
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '设备规则';
USE materin;
ALTER TABLE device ADD COLUMN secret VARCHAR(64) NULL AFTER device_key;
USE materin;
CREATE TABLE IF NOT EXISTS device_command_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    request_id  VARCHAR(64)  NOT NULL,
    device_id   BIGINT       NULL,
    direction   TINYINT      NOT NULL DEFAULT 2,
    payload     MEDIUMTEXT   NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cmd_log_rid (request_id)
) ENGINE = InnoDB COMMENT '命令日志（大报文回执落库）';
USE materin;
CREATE TABLE IF NOT EXISTS open_app (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    app_key     VARCHAR(64)  NOT NULL,
    app_secret  VARCHAR(128) NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_app_key (app_key)
) ENGINE = InnoDB COMMENT '开发者应用';

CREATE TABLE IF NOT EXISTS open_api (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    method      VARCHAR(8)   NOT NULL,
    path        VARCHAR(255) NOT NULL,
    summary     VARCHAR(255) NULL,
    tag         VARCHAR(64)  NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_api (method, path)
) ENGINE = InnoDB COMMENT '平台接口清单';

CREATE TABLE IF NOT EXISTS open_app_api (
    app_id BIGINT NOT NULL,
    api_id BIGINT NOT NULL,
    PRIMARY KEY (app_id, api_id)
) ENGINE = InnoDB COMMENT '应用-接口授权';

-- 开发者中心菜单（幂等种子）
INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '开发者中心', 0, 'catalog', '/developer', NULL, NULL, 1, 8,
       '{"title":"开发者中心","icon":"lucide:puzzle","order":8}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/developer');
SET @dev = (SELECT id FROM sys_menu WHERE path='/developer');
INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '应用管理', @dev, 'menu', '/developer/apps', '/developer/apps/index', 'AC_200100', 1, 1,
       '{"title":"应用管理","icon":"lucide:app-window","order":1}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/developer/apps');
INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '接口授权', @dev, 'menu', '/developer/apis', '/developer/apis/index', 'AC_200110', 1, 2,
       '{"title":"接口授权","icon":"lucide:key-round","order":2}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/developer/apis');
