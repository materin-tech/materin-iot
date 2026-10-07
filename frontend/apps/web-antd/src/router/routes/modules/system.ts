import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'ion:settings-outline',
      order: 9997,
      title: $t('system.title'),
    },
    name: 'System',
    path: '/system',
    children: [
      {
        path: 'user',
        name: 'SystemUser',
        meta: {
          icon: 'mdi:user',
          title: $t('system.user.title'),
        },
        component: () => import('#/views/system/user/list.vue'),
      },
      {
        path: 'role',
        name: 'SystemRole',
        meta: {
          icon: 'mdi:account-group',
          title: $t('system.role.title'),
        },
        component: () => import('#/views/system/role/list.vue'),
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        meta: {
          icon: 'mdi:menu',
          title: $t('system.menu.title'),
        },
        component: () => import('#/views/system/menu/list.vue'),
      },
      {
        path: 'org',
        name: 'SystemOrg',
        meta: {
          icon: 'charm:organisation',
          title: $t('system.org.title'),
        },
        component: () => import('#/views/system/org/list.vue'),
      },
      {
        path: 'security',
        name: 'SystemSecurity',
        meta: {
          icon: 'ion:shield-checkmark-outline',
          title: $t('system.security.title'),
        },
        component: () => import('#/views/system/security/index.vue'),
      },
    ],
  },
];

export default routes;
