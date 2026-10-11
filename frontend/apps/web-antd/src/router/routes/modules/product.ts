/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
