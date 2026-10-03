import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

/** 个人中心（不在菜单显示，从 header 用户下拉进入） */
const routes: RouteRecordRaw[] = [
  {
    component: () => import('#/views/_core/profile/index.vue'),
    meta: {
      hideInMenu: true,
      icon: 'lucide:user',
      order: 999,
      title: $t('page.auth.profile'),
    },
    name: 'Profile',
    path: '/profile',
  },
];

export default routes;
