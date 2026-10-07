-- V9: OTA 菜单（挂设备管理目录下）

SET @device = (SELECT id FROM sys_menu WHERE path = '/device');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '固件升级', @device, 'menu', '/ota/packages', 'ota/packages', NULL, 1, 6,
       '{"name":"OtaPackages","title":"page.ota.packages.title","icon":"lucide:package","order":5}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/ota/packages');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '升级任务', @device, 'menu', '/ota/tasks', 'ota/tasks', NULL, 1, 7,
       '{"name":"OtaTasks","title":"page.ota.tasks.title","icon":"lucide:rocket","order":6}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/ota/tasks');
