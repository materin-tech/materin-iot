import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:package',
      order: 10,
      title: $t('page.product.title'),
    },
    name: 'Product',
    path: '/product',
    children: [
      {
        path: 'standard',
        name: 'ProductStandard',
        meta: {
          icon: 'lucide:package-check',
          title: $t('page.product.standard'),
        },
        component: () => import('#/views/product/standard/list.vue'),
      },
      {
        path: 'custom',
        name: 'ProductCustom',
        meta: {
          icon: 'lucide:package-plus',
          title: $t('page.product.custom'),
        },
        component: () => import('#/views/product/custom/list.vue'),
      },
      {
        path: 'thing-model',
        name: 'ProductThingModel',
        meta: {
          icon: 'lucide:boxes',
          title: $t('page.product.thingModel'),
        },
        component: () => import('#/views/product/thing-model/list.vue'),
      },
    ],
  },
];

export default routes;
