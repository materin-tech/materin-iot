import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:puzzle',
      order: 8,
      title: '开发者中心',
    },
    name: 'Developer',
    path: '/developer',
    children: [
      {
        path: 'apps',
        name: 'DeveloperApps',
        meta: {
          icon: 'lucide:app-window',
          title: '应用管理',
        },
        component: () => import('#/views/developer/apps/index.vue'),
      },
      {
        path: 'apis',
        name: 'DeveloperApis',
        meta: {
          icon: 'lucide:key-round',
          title: '接口授权',
        },
        component: () => import('#/views/developer/apis/index.vue'),
      },
    ],
  },
];

export default routes;
