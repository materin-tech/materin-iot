-- V10: OTA 菜单重构为「固件管理」目录 + 升级计划支持按产品/按设备两种圈选

-- ============ 1. 任务表增加圈选口径 ============
ALTER TABLE ota_task
    ADD COLUMN scope        VARCHAR(16)  NOT NULL DEFAULT 'device' COMMENT 'product=按产品统一 device=按设备单独',
    ADD COLUMN product_id   BIGINT       NULL,
    ADD COLUMN product_name VARCHAR(128) NOT NULL DEFAULT '';

-- ============ 2. 菜单重构 ============
DELETE FROM sys_menu WHERE path IN ('/ota/packages', '/ota/tasks');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '固件管理', @device, 'catalog', '/firmware', NULL, NULL, 1, 6,
       '{"name":"Firmware","title":"page.ota.title","icon":"lucide:package","order":6}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/firmware');

SET @fw = (SELECT id FROM sys_menu WHERE path = '/firmware');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '固件包', @fw, 'menu', '/firmware/packages', 'ota/packages', NULL, 1, 1,
       '{"name":"OtaPackages","title":"page.ota.packages.title","icon":"lucide:package-check","order":1}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/firmware/packages');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '升级计划', @fw, 'menu', '/firmware/tasks', 'ota/tasks', NULL, 1, 2,
       '{"name":"OtaTasks","title":"page.ota.tasks.title","icon":"lucide:rocket","order":2}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/firmware/tasks');
