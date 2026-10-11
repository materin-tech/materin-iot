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

export namespace DeveloperApi {
  export interface AppItem {
    [key: string]: any;
    id: string;
    name: string;
    appKey: string;
    status: 0 | 1;
    remark?: string;
    createTime: string;
  }

  export interface AppCreated {
    appKey: string;
    appSecret: string;
    [key: string]: any;
  }
}

async function getAppList(params: Record<string, any>) {
  return requestClient.get('/open/apps/list', { params });
}

async function createApp(data: { name: string; remark?: string }) {
  return requestClient.post<DeveloperApi.AppCreated>('/open/apps', data);
}

async function getAppCredential(id: string) {
  return requestClient.get<any>(`/open/apps/${id}/credential`);
}

async function resetSecret(id: string) {
  return requestClient.post<DeveloperApi.AppCreated>(`/open/apps/${id}/secret/reset`);
}

async function deleteApp(id: string) {
  return requestClient.delete(`/open/apps/${id}`);
}

async function getApiList(params: Record<string, any>) {
  return requestClient.get('/open/apis/list', { params });
}

async function syncApis() {
  return requestClient.post<number>('/open/apis/sync');
}

async function getAuthorizedApis(appId: string) {
  return requestClient.get<string[]>(`/open/apps/${appId}/apis`);
}

async function saveApiAuth(appId: string, apiIds: string[]) {
  return requestClient.put(`/open/apps/${appId}/apis`, { apiIds });
}

async function grantApis(appId: string, apiIds: string[]) {
  return requestClient.post<number>(`/open/apps/${appId}/apis/grant`, { apiIds });
}

async function revokeApis(appId: string, apiIds: string[]) {
  return requestClient.post<number>(`/open/apps/${appId}/apis/revoke`, { apiIds });
}

export {
  createApp, deleteApp, getAppCredential, getApiDetail, getApiList, getAppList,
  getAuthorizedApis, grantApis, resetSecret, revokeApis, saveApiAuth, syncApis,
};

async function getApiDetail(method: string, path: string) {
  return requestClient.get('/open/apis/detail', { params: { method, path } });
}
