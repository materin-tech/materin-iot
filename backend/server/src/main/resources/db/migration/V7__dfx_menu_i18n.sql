-- V7: DFX 菜单标题改走前端 i18n（meta.title 存 key，由语言包提供多语言）
UPDATE sys_menu SET meta = '{"name":"Dfx","title":"page.dfx.title","icon":"lucide:activity","order":30}'
  WHERE path = '/dfx';
UPDATE sys_menu SET meta = '{"name":"DfxComponents","title":"page.dfx.components.title","icon":"lucide:heart-pulse","order":1}'
  WHERE path = '/dfx/components';
UPDATE sys_menu SET meta = '{"name":"DfxMetrics","title":"page.dfx.metrics.title","icon":"lucide:line-chart","order":2}'
  WHERE path = '/dfx/metrics';
UPDATE sys_menu SET meta = '{"name":"DfxRules","title":"page.dfx.rules.title","icon":"lucide:bell-plus","order":3}'
  WHERE path = '/dfx/rules';
