-- V4: 全量菜单种子（数据库驱动菜单 + RBAC）
-- 目标：所有菜单/权限点统一入库，前端 backend 模式按当前用户角色下发路由。
-- 幂等：按 path 判重；已存在的乱码行（V2 双重编码事故）一并修复。

-- ============ 1. 修复 V2 乱码行（UTF-8 双重编码）并补齐 meta.name ============
UPDATE sys_menu SET name = '开发者中心',
  meta = '{"name":"Developer","title":"开发者中心","icon":"lucide:puzzle","order":8}'
  WHERE path = '/developer' AND name <> '开发者中心';
UPDATE sys_menu SET name = '应用管理',
  meta = '{"name":"DeveloperApps","title":"应用管理","icon":"lucide:app-window","order":1}'
  WHERE path = '/developer/apps' AND name <> '应用管理';
UPDATE sys_menu SET name = '接口授权',
  meta = '{"name":"DeveloperApis","title":"接口授权","icon":"lucide:key-round","order":2}'
  WHERE path = '/developer/apis' AND name <> '接口授权';

-- 旧行 meta 无 name 字段的补齐（幂等）
UPDATE sys_menu SET meta = JSON_UNQUOTE(JSON_SET(CAST(meta AS JSON), '$.name', 'Developer'))
  WHERE path = '/developer' AND JSON_EXTRACT(CAST(meta AS JSON), '$.name') IS NULL;
UPDATE sys_menu SET meta = JSON_UNQUOTE(JSON_SET(CAST(meta AS JSON), '$.name', 'DeveloperApps'))
  WHERE path = '/developer/apps' AND JSON_EXTRACT(CAST(meta AS JSON), '$.name') IS NULL;
UPDATE sys_menu SET meta = JSON_UNQUOTE(JSON_SET(CAST(meta AS JSON), '$.name', 'DeveloperApis'))
  WHERE path = '/developer/apis' AND JSON_EXTRACT(CAST(meta AS JSON), '$.name') IS NULL;

-- ============ 2. 概览 Dashboard ============
INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '概览', 0, 'catalog', '/dashboard', NULL, NULL, 1, -1,
       '{"name":"Dashboard","title":"概览","icon":"lucide:layout-dashboard","order":-1}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dashboard');

SET @dashboard = (SELECT id FROM sys_menu WHERE path = '/dashboard');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '分析页', @dashboard, 'menu', '/dashboard/analytics', 'dashboard/analytics/index', NULL, 1, 1,
       '{"name":"Analytics","title":"分析页","icon":"lucide:area-chart","order":0,"affixTab":true}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dashboard/analytics');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '工作台', @dashboard, 'menu', '/dashboard/workspace', 'dashboard/workspace/index', NULL, 1, 2,
       '{"name":"Workspace","title":"工作台","icon":"carbon:workspace","order":1}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dashboard/workspace');

-- ============ 3. 产品管理 ============
INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '产品管理', 0, 'catalog', '/product', NULL, NULL, 1, 10,
       '{"name":"Product","title":"产品管理","icon":"lucide:package","order":10}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/product');

SET @product = (SELECT id FROM sys_menu WHERE path = '/product');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '标准产品', @product, 'menu', '/product/standard', 'product/standard/list', NULL, 1, 1,
       '{"name":"ProductStandard","title":"标准产品","icon":"lucide:package-check","order":0}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/product/standard');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '定制产品', @product, 'menu', '/product/custom', 'product/custom/list', NULL, 1, 2,
       '{"name":"ProductCustom","title":"定制产品","icon":"lucide:package-plus","order":1}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/product/custom');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '物模型', @product, 'menu', '/product/thing-model', 'product/thing-model/list', NULL, 1, 3,
       '{"name":"ProductThingModel","title":"物模型","icon":"lucide:boxes","order":2}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/product/thing-model');

-- ============ 4. 设备管理 ============
INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '设备管理', 0, 'catalog', '/device', NULL, NULL, 1, 20,
       '{"name":"Device","title":"设备管理","icon":"lucide:cpu","order":20}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/device');

SET @device = (SELECT id FROM sys_menu WHERE path = '/device');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '设备列表', @device, 'menu', '/device/list', 'device/list', NULL, 1, 1,
       '{"name":"DeviceList","title":"设备列表","icon":"lucide:list","order":0}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/device/list');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '设备调试', @device, 'menu', '/device/debug', 'device/debug', NULL, 1, 2,
       '{"name":"DeviceDebug","title":"设备调试","icon":"lucide:terminal","order":1}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/device/debug');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '设备告警', @device, 'menu', '/device/alert', 'device/alert', NULL, 1, 3,
       '{"name":"DeviceAlert","title":"设备告警","icon":"lucide:bell-ring","order":2}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/device/alert');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '设备规则', @device, 'menu', '/device/rule', 'device/rule', NULL, 1, 4,
       '{"name":"DeviceRule","title":"设备规则","icon":"lucide:git-branch","order":3}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/device/rule');

-- ============ 5. 个人中心（hideInMenu，header 用户下拉进入） ============
INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '个人中心', 0, 'menu', '/profile', '_core/profile/index', NULL, 1, 999,
       '{"name":"Profile","title":"个人中心","icon":"lucide:user","order":999,"hideInMenu":true}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/profile');

-- ============ 6. 系统管理（含页面级权限码，角色-菜单绑定即 RBAC 授权） ============
INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '系统管理', 0, 'catalog', '/system', NULL, NULL, 1, 9997,
       '{"name":"System","title":"系统管理","icon":"ion:settings-outline","order":9997}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system');

SET @system = (SELECT id FROM sys_menu WHERE path = '/system');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '用户管理', @system, 'menu', '/system/user', 'system/user/list', 'AC_100100', 1, 1,
       '{"name":"SystemUser","title":"用户管理","icon":"mdi:user","order":1}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/user');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '角色管理', @system, 'menu', '/system/role', 'system/role/list', 'AC_100110', 1, 2,
       '{"name":"SystemRole","title":"角色管理","icon":"mdi:account-group","order":2}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/role');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '菜单管理', @system, 'menu', '/system/menu', 'system/menu/list', 'AC_100120', 1, 3,
       '{"name":"SystemMenu","title":"菜单管理","icon":"mdi:menu","order":3}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/menu');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '组织管理', @system, 'menu', '/system/org', 'system/org/list', 'AC_100010', 1, 4,
       '{"name":"SystemOrg","title":"组织管理","icon":"charm:organisation","order":4}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/org');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '安全设置', @system, 'menu', '/system/security', 'system/security/index', 'AC_100130', 1, 5,
       '{"name":"SystemSecurity","title":"安全设置","icon":"ion:shield-checkmark-outline","order":5}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/security');
