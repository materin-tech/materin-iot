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
