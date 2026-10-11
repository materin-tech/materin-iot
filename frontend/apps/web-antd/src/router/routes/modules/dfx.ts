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
