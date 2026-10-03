import type { Recordable } from '@vben/types';

import type { ProductApi } from '#/api/product/product';

import { requestClient } from '#/api/request';

export namespace DeviceAlertApi {
  export interface DeviceAlert {
    [key: string]: any;
    id: string;
    deviceId: string;
    deviceName: string;
    /** info-信息 warn-警告 error-故障 */
    level: 'error' | 'info' | 'warn';
    content: string;
    /** 0-未处理 1-已处理 */
    status: 0 | 1;
    createTime: string;
  }
}

/** 获取事件告警列表（分页） */
async function getDeviceAlertList(params: Recordable<any>) {
  return requestClient.get<ProductApi.PageResult<DeviceAlertApi.DeviceAlert>>(
    '/device/alert/list',
    { params },
  );
}

/** 处理告警（标记已处理等） */
async function updateDeviceAlert(
  id: string,
  data: Partial<DeviceAlertApi.DeviceAlert>,
) {
  return requestClient.put(`/device/alert/${id}`, data);
}

export { getDeviceAlertList, updateDeviceAlert };
