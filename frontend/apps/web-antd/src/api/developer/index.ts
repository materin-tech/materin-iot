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
