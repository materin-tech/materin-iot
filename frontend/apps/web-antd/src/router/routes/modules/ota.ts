import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

/** 固件管理域路由（backend 菜单模式下与 sys_menu V10 记录一致，挂设备管理下） */
const routes: RouteRecordRaw[] = [
  {
    path: '/firmware',
    name: 'Firmware',
    meta: { icon: 'lucide:package', order: 6, title: $t('page.ota.title') },
    children: [
      {
        path: 'packages',
        name: 'OtaPackages',
        meta: { icon: 'lucide:package-check', title: $t('page.ota.packages.title') },
        component: () => import('#/views/ota/packages.vue'),
      },
      {
        path: 'tasks',
        name: 'OtaTasks',
        meta: { icon: 'lucide:rocket', title: $t('page.ota.tasks.title') },
        component: () => import('#/views/ota/tasks.vue'),
      },
    ],
  },
];

export default routes;
