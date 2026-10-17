-- V18: DFX v2 菜单 —— 健康大盘 / 采集目标 / 通知渠道（幂等插入，沿用 V6 模式）

SET @dfx = (SELECT id FROM sys_menu WHERE path = '/dfx' LIMIT 1);

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '健康大盘', COALESCE(@dfx, 0), 'menu', '/dfx/overview', 'dfx/overview', NULL, 1, 0,
       '{"name":"DfxOverview","title":"page.dfx.overview.title","icon":"lucide:gauge","order":0}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dfx/overview');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '采集目标', @dfx, 'menu', '/dfx/targets', 'dfx/targets', NULL, 1, 4,
       '{"name":"DfxTargets","title":"page.dfx.targets.title","icon":"lucide:radio-tower","order":4}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dfx/targets');

INSERT INTO sys_menu (name, pid, type, path, component, auth_code, status, sort, meta)
SELECT '通知渠道', @dfx, 'menu', '/dfx/channels', 'dfx/channels', NULL, 1, 5,
       '{"name":"DfxChannels","title":"page.dfx.channels.title","icon":"lucide:bell-ring","order":5}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dfx/channels');

-- 健康大盘应挂为 /dfx 下第一个子页
UPDATE sys_menu SET pid = @dfx WHERE path = '/dfx/overview' AND pid = 0;
