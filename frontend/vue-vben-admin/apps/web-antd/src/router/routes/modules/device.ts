import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:cpu',
      order: 20,
      title: $t('page.device.title'),
    },
    name: 'Device',
    path: '/device',
    children: [
      {
        path: 'list',
        name: 'DeviceList',
        meta: {
          icon: 'lucide:list',
          title: $t('page.device.list'),
        },
        component: () => import('#/views/device/list.vue'),
      },
      {
        path: 'debug',
        name: 'DeviceDebug',
        meta: {
          icon: 'lucide:terminal',
          title: $t('page.device.debug'),
        },
        component: () => import('#/views/device/debug.vue'),
      },
      {
        path: 'alert',
        name: 'DeviceAlert',
        meta: {
          icon: 'lucide:bell-ring',
          title: $t('page.device.alert'),
        },
        component: () => import('#/views/device/alert.vue'),
      },
      {
        path: 'rule',
        name: 'DeviceRule',
        meta: {
          icon: 'lucide:git-branch',
          title: $t('page.device.rule'),
        },
        component: () => import('#/views/device/rule.vue'),
      },
    ],
  },
];

export default routes;
