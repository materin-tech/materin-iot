-- V2: 开发者中心菜单种子（幂等：已存在则跳过）

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
