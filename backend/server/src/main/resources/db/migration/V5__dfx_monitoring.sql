-- V5: 设备 DFX 健康监控（docs/dfx-monitoring-design.md）
-- 1) 结构化阈值告警规则（从自由文本 device_rule 升级，可被执行引擎评估）
-- 2) SNMP 轮询目标配置（Linux 网关类设备，平台作为 SNMP manager 主动拉取）

-- ============ 1. DFX 阈值告警规则 ============
CREATE TABLE IF NOT EXISTS dfx_alert_rule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id   BIGINT       NOT NULL,
    device_name VARCHAR(128) NOT NULL DEFAULT '',
    metric      VARCHAR(64)  NOT NULL COMMENT '统一指标字典名，如 cpu_usage_pct',
    comparator  VARCHAR(2)   NOT NULL DEFAULT 'gt' COMMENT 'gt=大于 lt=小于',
    threshold   DOUBLE       NOT NULL,
    level       VARCHAR(8)   NOT NULL DEFAULT 'warn' COMMENT 'info/warn/error',
    suppress_seconds INT     NOT NULL DEFAULT 600 COMMENT '同设备同指标告警抑制窗口(秒)',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DFX 阈值告警规则';

-- ============ 2. SNMP 轮询目标 ============
CREATE TABLE IF NOT EXISTS dfx_snmp_target (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id       BIGINT       NOT NULL,
    device_name     VARCHAR(128) NOT NULL DEFAULT '',
    host            VARCHAR(128) NOT NULL,
    port            INT          NOT NULL DEFAULT 161,
    version         VARCHAR(8)   NOT NULL DEFAULT 'v2c',
    community       VARCHAR(64)  NOT NULL DEFAULT 'public',
    interval_seconds INT         NOT NULL DEFAULT 60,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    remark          VARCHAR(255) NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DFX SNMP 轮询目标';

-- ============ 3. 设备运维菜单（挂设备管理目录下） ============
SET @device = (SELECT id FROM sys_menu WHERE path = '/device');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '设备运维', @device, 'menu', '/device/monitor', 'device/monitor', NULL, 1, 5,
       '{"name":"DeviceMonitor","title":"设备运维","icon":"lucide:activity","order":4}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/device/monitor');
