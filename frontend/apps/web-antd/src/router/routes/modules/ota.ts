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
