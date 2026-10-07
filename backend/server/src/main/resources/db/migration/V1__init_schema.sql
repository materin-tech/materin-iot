-- V1: 格物 IoT 平台全量基线 schema（合并 init.sql + upgrade-2..6 的最终形态）
-- 已有存量库通过 baseline-on-migrate 跳过本脚本；新库由 Flyway 全量创建。

CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(128) NULL COMMENT 'BCrypt 散列',
    nickname    VARCHAR(64)  NULL,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
    org_id      BIGINT       NULL COMMENT '组织 ID',
    remark      VARCHAR(255) NULL,
    phone       VARCHAR(20)  NULL COMMENT '实名手机号',
    id_type     VARCHAR(16)  NULL COMMENT '证件类型：ID_CARD-身份证 PASSPORT-护照 OTHER-其他',
    id_no       VARCHAR(255) NULL COMMENT '证件号（AES 加密存储）',
    password_update_time DATETIME NULL COMMENT '密码最后修改时间（空表示首次登录需修改）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB COMMENT '系统用户';

CREATE TABLE sys_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_key   VARCHAR(128) NOT NULL COMMENT '参数键',
    config_value VARCHAR(255) NOT NULL COMMENT '参数值',
    remark       VARCHAR(255) NULL COMMENT '说明',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE = InnoDB COMMENT '系统参数';

CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL COMMENT '角色名',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_name (name)
) ENGINE = InnoDB COMMENT '角色';

CREATE TABLE sys_org (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL COMMENT '组织名',
    pid         BIGINT       NULL COMMENT '父级组织 ID（根为空/0）',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '组织';

CREATE TABLE sys_menu (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL COMMENT '菜单名',
    pid         BIGINT       NULL COMMENT '父级菜单 ID',
    type        VARCHAR(16)  NOT NULL DEFAULT 'menu' COMMENT 'catalog/menu/button',
    path        VARCHAR(128) NULL COMMENT '路由路径',
    component   VARCHAR(128) NULL COMMENT '前端组件路径',
    auth_code   VARCHAR(64)  NULL COMMENT '权限标识',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    meta        TEXT         NULL COMMENT '元信息 JSON（title/icon/order 等）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '菜单/权限点';

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB COMMENT '用户-角色';

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE = InnoDB COMMENT '角色-菜单';

CREATE TABLE device (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    device_key   VARCHAR(64)  NOT NULL COMMENT '设备唯一标识（协议层寻址）',
    secret       VARCHAR(64)  NULL COMMENT '设备密钥',
    name         VARCHAR(128) NOT NULL,
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未激活 / 1 在线 / 2 离线',
    product_id   BIGINT       NULL COMMENT '产品 ID',
    product_name VARCHAR(128) NULL COMMENT '产品名称（冗余）',
    firmware     VARCHAR(64)  NULL COMMENT '固件版本',
    last_online  VARCHAR(64)  NULL COMMENT '最后在线时间',
    remark       VARCHAR(255) NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_key (device_key)
) ENGINE = InnoDB COMMENT '设备台账';

CREATE TABLE product (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(128) NOT NULL,
    product_key  VARCHAR(64)  NOT NULL,
    category     VARCHAR(64)  NULL,
    protocol     VARCHAR(32)  NULL,
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0-开发中 1-已发布',
    source       VARCHAR(16)  NOT NULL DEFAULT 'custom' COMMENT 'standard/custom',
    device_count INT          NOT NULL DEFAULT 0,
    remark       VARCHAR(255) NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_key (product_key)
) ENGINE = InnoDB COMMENT '产品';

CREATE TABLE thing_model (
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

CREATE TABLE device_alert (
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

CREATE TABLE device_rule (
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

CREATE TABLE device_command_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    request_id  VARCHAR(64)  NOT NULL,
    device_id   BIGINT       NULL,
    direction   TINYINT      NOT NULL DEFAULT 2,
    payload     MEDIUMTEXT   NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cmd_log_rid (request_id)
) ENGINE = InnoDB COMMENT '命令日志（大报文回执落库）';

CREATE TABLE open_app (
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

CREATE TABLE open_api (
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

CREATE TABLE open_app_api (
    app_id BIGINT NOT NULL,
    api_id BIGINT NOT NULL,
    PRIMARY KEY (app_id, api_id)
) ENGINE = InnoDB COMMENT '应用-接口授权';
