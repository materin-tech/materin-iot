import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

/** DFX 监控域路由（backend 菜单模式下与 sys_menu V6 记录一致） */
const routes: RouteRecordRaw[] = [
  {
    component: () => import('#/layouts/basic.vue'),
    meta: {
      icon: 'lucide:activity',
      order: 30,
      title: $t('page.dfx.title'),
    },
    name: 'Dfx',
    path: '/dfx',
    children: [
      {
        path: 'components',
        name: 'DfxComponents',
        meta: {
          icon: 'lucide:heart-pulse',
          title: $t('page.dfx.components.title'),
        },
        component: () => import('#/views/dfx/components.vue'),
      },
      {
        path: 'metrics',
        name: 'DfxMetrics',
        meta: {
          icon: 'lucide:line-chart',
          title: $t('page.dfx.metrics.title'),
        },
        component: () => import('#/views/dfx/metrics.vue'),
      },
      {
        path: 'rules',
        name: 'DfxRules',
        meta: {
          icon: 'lucide:bell-plus',
          title: $t('page.dfx.rules.title'),
        },
        component: () => import('#/views/dfx/rules.vue'),
      },
    ],
  },
];

export default routes;
