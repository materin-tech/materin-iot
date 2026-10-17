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

export namespace DeviceAlertApi {
  export interface DeviceAlert {
    [key: string]: any;
    id: string;
    deviceId: string;
    deviceName: string;
    /** info-信息 warn-警告 error-故障 */
    level: 'error' | 'info' | 'warn';
    content: string;
    /** 0-活跃 1-已确认 2-已恢复 */
    status: 0 | 1 | 2;
    ruleId?: number;
    metric?: string;
    lastValue?: string;
    triggerCount?: number;
    lastTime?: string;
    ackTime?: string;
    clearTime?: string;
    clearReason?: string;
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

/** 告警统计（总量/生命周期/级别分布，单次请求） */
async function getDeviceAlertStats() {
  return requestClient.get<Record<string, number>>('/device/alert/stats');
}

export { getDeviceAlertList, getDeviceAlertStats, updateDeviceAlert };
