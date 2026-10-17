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

import { requestClient } from '#/api/request';

/** DFX 健康监控 API（docs/dfx-monitoring-design.md §7） */
export namespace DfxApi {
  export interface DfxTarget {
    id?: number;
    deviceId: number;
    deviceName?: string;
    host: string;
    port?: number;
    version?: string;
    community?: string;
    v3User?: string;
    v3AuthProtocol?: string;
    v3AuthPassphrase?: string;
    v3PrivProtocol?: string;
    v3PrivPassphrase?: string;
    intervalSeconds?: number;
    enabled?: number;
    remark?: string;
  }
  export interface DfxRule {
    id?: number;
    deviceId?: number;
    deviceName?: string;
    productId?: number;
    metric: string;
    comparator: string;
    threshold: number;
    threshold2?: number;
    durationSeconds?: number;
    clearComparator?: string;
    clearThreshold?: number;
    messageTemplate?: string;
    level?: string;
    suppressSeconds?: number;
    enabled?: number;
    remark?: string;
  }
  export interface DfxAccessHealth {
    name: string;
    enabled: boolean;
    status: 'UP' | 'DOWN' | 'DISABLED' | string;
    detail: string;
    lastIngestAt: number | null;
  }
  export interface DfxHealthResponse {
    components: DfxAccessHealth[];
    pipeline: Record<string, { count: number; lastIngestAt: number }>;
  }
  export interface DfxMetricConfig {
    id?: number;
    metric: string;
    name: string;
    unit?: string;
    dataType?: string;
    description?: string;
  }
  export interface DfxChartMetric {
    key: string;
    name: string;
  }
  export interface DfxChartConfig {
    title: string;
    type?: 'line' | 'bar';
    unit?: string;
    yMax?: number;
    agg?: string;
    interval?: number;
    metrics: DfxChartMetric[];
  }
  export interface DfxDashboardConfig {
    charts: DfxChartConfig[];
  }
  export interface DfxDashboard {
    id?: number;
    productId: number;
    productName?: string;
    name: string;
    configJson: string;
    remark?: string;
  }
  export type NotifyChannelType = 'email' | 'inapp' | 'webhook';
  export interface NotifyChannel {
    id?: number;
    name: string;
    type: NotifyChannelType;
    configJson: string;
    minLevel?: string;
    enabled?: number;
    remark?: string;
  }
  export interface DfxNotification {
    id: number;
    title: string;
    content: string;
    level: string;
    alertId?: number;
    readFlag: number;
    createTime?: string;
  }
  export interface DfxOverview {
    devices?: { total: number; online: number; offline: number; inactive: number };
    alerts?: Record<string, number>;
    trend?: { date: string; total: number }[];
    components: DfxAccessHealth[];
    pipeline: Record<string, { count: number; lastIngestAt: number }>;
  }
  export interface DfxHistoryPoint {
    time: number;
    [key: string]: number | string | null;
  }
}

async function getDfxLatest(params: { deviceKey: string }) {
  return requestClient.get<Record<string, string>>('/device/dfx/latest', { params });
}
async function getDfxHistory(params: Record<string, any>) {
  return requestClient.get<DfxApi.DfxHistoryPoint[]>('/device/dfx/history', { params });
}
async function getDfxTargetList(params: Record<string, any>) {
  return requestClient.get<{ items: DfxApi.DfxTarget[]; total: number }>(
    '/device/dfx/target/list', { params });
}
async function createDfxTarget(data: Omit<DfxApi.DfxTarget, 'id'>) {
  return requestClient.post('/device/dfx/target', data);
}
async function deleteDfxTarget(id: number) {
  return requestClient.delete(`/device/dfx/target/${id}`);
}
async function getDfxRuleList(params: Record<string, any>) {
  return requestClient.get<{ items: DfxApi.DfxRule[]; total: number }>(
    '/device/dfx/rule/list', { params });
}
async function createDfxRule(data: Omit<DfxApi.DfxRule, 'id'>) {
  return requestClient.post('/device/dfx/rule', data);
}
async function deleteDfxRule(id: number) {
  return requestClient.delete(`/device/dfx/rule/${id}`);
}
async function getDfxMetricList() {
  return requestClient.get<DfxApi.DfxMetricConfig[]>('/device/dfx/metric/list');
}
async function saveDfxMetric(data: DfxApi.DfxMetricConfig) {
  return requestClient.post('/device/dfx/metric', data);
}
async function getDfxDashboards(productId: number) {
  return requestClient.get<DfxApi.DfxDashboard[]>('/device/dfx/dashboard/list', { params: { productId } });
}
async function saveDfxDashboard(data: Partial<DfxApi.DfxDashboard>) {
  return requestClient.post('/device/dfx/dashboard', data);
}
async function getDfxHealth() {
  return requestClient.get<DfxApi.DfxHealthResponse>('/device/dfx/health');
}
async function updateDfxRule(id: number, data: Partial<DfxApi.DfxRule>) {
  return requestClient.put(`/device/dfx/rule/${id}`, data);
}
async function updateDfxTarget(id: number, data: Partial<DfxApi.DfxTarget>) {
  return requestClient.put(`/device/dfx/target/${id}`, data);
}
async function getDfxOverview(trendDays = 7) {
  return requestClient.get<DfxApi.DfxOverview>('/device/dfx/overview', { params: { trendDays } });
}
async function getDfxChannelList() {
  return requestClient.get<DfxApi.NotifyChannel[]>('/device/dfx/notify/channel/list');
}
async function createDfxChannel(data: Omit<DfxApi.NotifyChannel, 'id'>) {
  return requestClient.post('/device/dfx/notify/channel', data);
}
async function updateDfxChannel(id: number, data: Partial<DfxApi.NotifyChannel>) {
  return requestClient.put(`/device/dfx/notify/channel/${id}`, data);
}
async function deleteDfxChannel(id: number) {
  return requestClient.delete(`/device/dfx/notify/channel/${id}`);
}
async function testDfxChannel(id: number) {
  return requestClient.post(`/device/dfx/notify/channel/${id}/test`);
}
async function getDfxNotificationList(params: Record<string, any>) {
  return requestClient.get<{ items: DfxApi.DfxNotification[]; total: number }>(
    '/device/dfx/notification/list', { params });
}
async function getDfxNotificationUnread() {
  return requestClient.get<number>('/device/dfx/notification/unread-count');
}
async function readDfxNotification(id: number) {
  return requestClient.put(`/device/dfx/notification/read/${id}`);
}
async function readAllDfxNotifications() {
  return requestClient.put('/device/dfx/notification/read-all');
}
export {
  createDfxChannel, createDfxRule, createDfxTarget, deleteDfxChannel, deleteDfxRule,
  deleteDfxTarget, getDfxChannelList, getDfxHealth, getDfxHistory, getDfxLatest,
  getDfxMetricList, getDfxNotificationList, getDfxNotificationUnread, getDfxOverview,
  getDfxRuleList, getDfxTargetList, getDfxDashboards, readAllDfxNotifications,
  readDfxNotification, saveDfxDashboard, saveDfxMetric, testDfxChannel,
  updateDfxChannel, updateDfxRule, updateDfxTarget,
};
