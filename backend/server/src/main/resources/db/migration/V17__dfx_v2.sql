-- V17: DFX v2 - rule engine upgrade + alert lifecycle + notify channels + SNMP v3 + indexes
-- Benchmarks: ThingsBoard alarm model (trigger/clear conditions, duration, severity), HuaWei IoTDA ops checklist
-- NOTE: keep this file ASCII-only in comments and single-line statements.
--       Flyway's MySQL parser mis-splits multi-line statements after CJK comment content.

ALTER TABLE dfx_alert_rule MODIFY COLUMN comparator VARCHAR(8) NOT NULL DEFAULT 'gt' COMMENT 'gt/gte/lt/lte/eq/ne/between';
ALTER TABLE dfx_alert_rule ADD COLUMN threshold2 DOUBLE NULL COMMENT 'between upper bound';
ALTER TABLE dfx_alert_rule ADD COLUMN product_id BIGINT NULL COMMENT 'product-level rule, NULL for device-level';
ALTER TABLE dfx_alert_rule ADD COLUMN duration_seconds INT NOT NULL DEFAULT 0 COMMENT '0 = fire immediately, >0 = continuous breach seconds';
ALTER TABLE dfx_alert_rule ADD COLUMN clear_comparator VARCHAR(8) NULL COMMENT 'clear comparator, NULL = no auto clear';
ALTER TABLE dfx_alert_rule ADD COLUMN clear_threshold DOUBLE NULL COMMENT 'clear threshold';
ALTER TABLE dfx_alert_rule ADD COLUMN message_template VARCHAR(255) NULL COMMENT 'content template with device/metric/value/threshold slots';
ALTER TABLE dfx_alert_rule ADD INDEX idx_alert_rule_device (device_id);
ALTER TABLE dfx_alert_rule ADD INDEX idx_alert_rule_product (product_id);

ALTER TABLE device_alert ADD COLUMN rule_id BIGINT NULL COMMENT 'triggering rule id';
ALTER TABLE device_alert ADD COLUMN metric VARCHAR(64) NULL COMMENT 'alert metric key, e.g. snmp_poll/offline';
ALTER TABLE device_alert ADD COLUMN `last_value` VARCHAR(64) NULL COMMENT 'last breached value';
ALTER TABLE device_alert MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0-active 1-acked 2-cleared';
ALTER TABLE device_alert ADD COLUMN trigger_count INT NOT NULL DEFAULT 1 COMMENT 'trigger count for same rule';
ALTER TABLE device_alert ADD COLUMN last_time DATETIME NULL COMMENT 'last trigger time';
ALTER TABLE device_alert ADD COLUMN ack_time DATETIME NULL COMMENT 'ack time';
ALTER TABLE device_alert ADD COLUMN clear_time DATETIME NULL COMMENT 'clear time';
ALTER TABLE device_alert ADD COLUMN clear_reason VARCHAR(255) NULL COMMENT 'clear reason';
ALTER TABLE device_alert ADD INDEX idx_alert_device (device_id);
ALTER TABLE device_alert ADD INDEX idx_alert_status_time (status, create_time);
ALTER TABLE device_alert ADD INDEX idx_alert_rule_dev (rule_id, device_id);

ALTER TABLE dfx_snmp_target ADD COLUMN v3_user VARCHAR(64) NULL COMMENT 'v3 USM user';
ALTER TABLE dfx_snmp_target ADD COLUMN v3_auth_protocol VARCHAR(16) NULL COMMENT 'auth protocol MD5/SHA';
ALTER TABLE dfx_snmp_target ADD COLUMN v3_auth_passphrase VARCHAR(128) NULL COMMENT 'auth passphrase';
ALTER TABLE dfx_snmp_target ADD COLUMN v3_priv_protocol VARCHAR(16) NULL COMMENT 'priv protocol DES/AES128';
ALTER TABLE dfx_snmp_target ADD COLUMN v3_priv_passphrase VARCHAR(128) NULL COMMENT 'priv passphrase';
ALTER TABLE dfx_snmp_target ADD INDEX idx_snmp_target_device (device_id);

CREATE TABLE dfx_notify_channel (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL COMMENT 'channel name',
    type        VARCHAR(16)  NOT NULL COMMENT 'webhook/email/inapp',
    config_json TEXT         NULL COMMENT 'webhook url and format, email to and subjectPrefix',
    min_level   VARCHAR(8)   NOT NULL DEFAULT 'info' COMMENT 'alerts below this level are not pushed, info less than warn less than error',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '1-enabled 0-disabled',
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DFX alert notify channel';

-- built-in inapp channel (webhook/email channels are user-created)
INSERT INTO dfx_notify_channel (name, type, config_json, min_level, enabled, remark)
SELECT '站内通知', 'inapp', '{}', 'info', 1, '平台内置站内通知，可在通知铃铛查看'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dfx_notify_channel WHERE type = 'inapp');

CREATE TABLE dfx_notification (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    title       VARCHAR(128)  NOT NULL,
    content     VARCHAR(1024) NULL,
    level       VARCHAR(8)    NULL COMMENT 'info/warn/error',
    alert_id    BIGINT        NULL COMMENT 'related alert id',
    read_flag   TINYINT       NOT NULL DEFAULT 0 COMMENT '0-unread 1-read',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_notification_read (read_flag, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DFX in-app notification';

-- dashboard idempotency (guard against concurrent duplicates)
DELETE d1 FROM dfx_dashboard d1 JOIN dfx_dashboard d2
    ON d1.product_id = d2.product_id AND d1.name = d2.name AND d1.id > d2.id;
ALTER TABLE dfx_dashboard ADD UNIQUE INDEX uk_dashboard_product_name (product_id, name);
