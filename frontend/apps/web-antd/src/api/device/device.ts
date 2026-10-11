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

export namespace DeviceApi {
  /** 设备状态：0-未激活 1-在线 2-离线 */
  export type DeviceStatus = 0 | 1 | 2;

  export interface Device {
    [key: string]: any;
    id: string;
    name: string;
    /** 设备唯一标识 */
    deviceKey: string;
    productId: string;
    productName: string;
    status: DeviceStatus;
    firmware: string;
    lastOnline: string;
    remark?: string;
    createTime: string;
  }
}

/** 获取设备列表（分页） */
async function getDeviceList(params: Recordable<any>) {
  return requestClient.get<ProductApi.PageResult<DeviceApi.Device>>(
    '/device/list',
    { params },
  );
}

async function createDevice(data: Omit<DeviceApi.Device, 'id'>) {
  return requestClient.post('/device', data);
}

async function updateDevice(id: string, data: Omit<DeviceApi.Device, 'id'>) {
  return requestClient.put(`/device/${id}`, data);
}

async function deleteDevice(id: string) {
  return requestClient.delete(`/device/${id}`);
}

export { createDevice, deleteDevice, getDeviceList, importDevices, updateDevice };

/** 批量导入设备 */
async function importDevices(data: { items: any[] }) {
  return requestClient.post<{ failed: number; imported: number }>(
    '/device/import',
    data,
  );
}
