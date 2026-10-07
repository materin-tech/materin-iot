import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

/** OTA 升级路由（backend 菜单模式下与 sys_menu V9 记录一致，挂设备管理下） */
const routes: RouteRecordRaw[] = [
  {
    path: '/ota/packages',
    name: 'OtaPackages',
    meta: { icon: 'lucide:package', title: $t('page.ota.packages.title') },
    component: () => import('#/views/ota/packages.vue'),
  },
  {
    path: '/ota/tasks',
    name: 'OtaTasks',
    meta: { icon: 'lucide:rocket', title: $t('page.ota.tasks.title') },
    component: () => import('#/views/ota/tasks.vue'),
  },
];

export default routes;
