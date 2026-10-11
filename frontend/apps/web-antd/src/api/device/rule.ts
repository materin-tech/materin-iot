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

import type { Recordable } from '@vben/types';

import type { ProductApi } from '#/api/product/product';

import { requestClient } from '#/api/request';

export namespace DeviceRuleApi {
  export interface DeviceRule {
    [key: string]: any;
    id: string;
    name: string;
    /** 触发条件描述 */
    trigger: string;
    /** 执行动作描述 */
    action: string;
    /** 0-停用 1-启用 */
    status: 0 | 1;
    remark?: string;
    createTime: string;
  }
}

async function getDeviceRuleList(params: Recordable<any>) {
  return requestClient.get<ProductApi.PageResult<DeviceRuleApi.DeviceRule>>(
    '/device/rule/list',
    { params },
  );
}

async function createDeviceRule(data: Omit<DeviceRuleApi.DeviceRule, 'id'>) {
  return requestClient.post('/device/rule', data);
}

async function updateDeviceRule(
  id: string,
  data: Omit<DeviceRuleApi.DeviceRule, 'id'>,
) {
  return requestClient.put(`/device/rule/${id}`, data);
}

async function deleteDeviceRule(id: string) {
  return requestClient.delete(`/device/rule/${id}`);
}

export {
  createDeviceRule,
  deleteDeviceRule,
  getDeviceRuleList,
  updateDeviceRule,
};
