-- V6: DFX 指标字典配置 + 按产品 Dashboard 配置 + DFX 监控菜单重构

-- ============ 1. 指标字典（上报 key -> 汉字表述/单位/类型） ============
-- 上报指标不设白名单（未知 key 自动 TEXT 兜底入库）；本表定义"标准指标 + 自定义指标"的展示口径，
-- 监控指标页/告警规则页据此把 dfx_cpu_usage_pct 等关键词渲染为中文。
CREATE TABLE IF NOT EXISTS dfx_metric_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric      VARCHAR(128) NOT NULL UNIQUE COMMENT '上报指标 key（含 dfx_ 前缀）',
    name        VARCHAR(128) NOT NULL COMMENT '汉字表述',
    unit        VARCHAR(16)  NULL COMMENT '单位（%/s/kbps/…）',
    data_type   VARCHAR(16)  NOT NULL DEFAULT 'double' COMMENT 'double/long/text',
    description VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DFX 指标字典配置';

INSERT INTO dfx_metric_config (metric, name, unit, data_type, description) VALUES
    ('dfx_cpu_usage_pct', 'CPU 使用率', '%', 'double', '处理器使用率'),
    ('dfx_mem_usage_pct', '内存使用率', '%', 'double', '物理内存使用率'),
    ('dfx_swap_usage_pct', '交换分区使用率', '%', 'double', NULL),
    ('dfx_disk_usage_pct', '磁盘使用率', '%', 'double', '根分区使用率'),
    ('dfx_load_1m', '1分钟负载', '', 'double', '系统负载均值'),
    ('dfx_net_rx_kbps', '网络接收速率', 'kbps', 'double', NULL),
    ('dfx_net_tx_kbps', '网络发送速率', 'kbps', 'double', NULL),
    ('dfx_uptime_s', '运行时长', 's', 'long', '本次运行秒数'),
    ('dfx_heartbeat', '心跳', '', 'long', '存活标记')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============ 2. 按产品 Dashboard 配置（类 Grafana：布局完全由配置驱动） ============
-- 设备关联产品，监控指标页 = 选产品 -> 加载该产品 dashboard 配置 -> 选设备填充数据。
CREATE TABLE IF NOT EXISTS dfx_dashboard (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT       NOT NULL,
    product_name VARCHAR(128) NOT NULL DEFAULT '',
    name        VARCHAR(128) NOT NULL,
    config_json MEDIUMTEXT   NOT NULL COMMENT '{"charts":[{title,type,unit,yMax,agg,interval,metrics:[{key,name}]}]}',
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DFX 产品 Dashboard 配置';

-- ============ 3. 一级菜单「DFX监控」+ 二级菜单 ============
INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT 'DFX监控', 0, 'catalog', '/dfx', NULL, NULL, 1, 30,
       '{"name":"Dfx","title":"DFX监控","icon":"lucide:activity","order":30}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dfx');

SET @dfx = (SELECT id FROM sys_menu WHERE path = '/dfx');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '组件状态', @dfx, 'menu', '/dfx/components', 'dfx/components', NULL, 1, 1,
       '{"name":"DfxComponents","title":"组件状态","icon":"lucide:heart-pulse","order":1}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dfx/components');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '监控指标', @dfx, 'menu', '/dfx/metrics', 'dfx/metrics', NULL, 1, 2,
       '{"name":"DfxMetrics","title":"监控指标","icon":"lucide:line-chart","order":2}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dfx/metrics');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '告警规则', @dfx, 'menu', '/dfx/rules', 'dfx/rules', NULL, 1, 3,
       '{"name":"DfxRules","title":"告警规则","icon":"lucide:bell-plus","order":3}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dfx/rules');

-- 旧"设备运维"页拆分为 DFX 监控域，菜单下线（页面由 /dfx/* 承接）
DELETE FROM sys_menu WHERE path = '/device/monitor';
