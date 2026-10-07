import { requestClient } from '#/api/request';

/** OTA 升级 API（固件包走 storage 中间件对象存储） */
export namespace OtaApi {
  export interface OtaPackage {
    id?: number;
    name: string;
    productId: number;
    productName?: string;
    type?: string;
    module?: string;
    version: string;
    size?: number;
    signMethod?: string;
    signValue?: string;
    storageKey?: string;
    contentType?: string;
    description?: string;
    status?: number;
  }
  export interface OtaTask {
    id?: number;
    packageId: number;
    taskName: string;
    deviceCount?: number;
    status?: number;
  }
  export interface OtaTaskDevice {
    id?: number;
    taskId: number;
    deviceKey: string;
    deviceName: string;
    status: number;
    progress: number;
    message?: string;
    pushCount?: number;
  }
}

export const OTA_STATUS_TEXT: Record<number, string> = {
  0: '待推送', 1: '已推送', 2: '下载中', 3: '升级中', 4: '成功', 5: '失败', 6: '已取消',
};

async function getOtaPackages(params: Record<string, any>) {
  return requestClient.get<{ items: OtaApi.OtaPackage[]; total: number }>('/ota/package/list', { params });
}
async function uploadOtaPackage(form: FormData) {
  return requestClient.post<OtaApi.OtaPackage>('/ota/package', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}
async function deleteOtaPackage(id: number) {
  return requestClient.delete(`/ota/package/${id}`);
}
async function getOtaTasks(params: Record<string, any>) {
  return requestClient.get<{ items: OtaApi.OtaTask[]; total: number }>('/ota/task/list', { params });
}
async function createOtaTask(data: {
  packageName: string; productId?: number; taskName: string;
}) {
  return requestClient.post<OtaApi.OtaTask>('/ota/task', data);
}
async function getOtaCandidates(taskId: number, params: Record<string, any>) {
  return requestClient.get<{ items: any[]; total: number }>(
    `/ota/task/${taskId}/candidates`, { params });
}
async function addOtaTaskDevices(taskId: number, deviceIds: number[]) {
  return requestClient.post<{ added: number }>(`/ota/task/${taskId}/devices`, { deviceIds });
}
async function startOtaTask(taskId: number) {
  return requestClient.post(`/ota/task/${taskId}/start`);
}
async function pauseOtaTask(taskId: number) {
  return requestClient.post(`/ota/task/${taskId}/pause`);
}
async function resumeOtaTask(taskId: number) {
  return requestClient.post(`/ota/task/${taskId}/resume`);
}
async function terminateOtaTask(taskId: number) {
  return requestClient.post(`/ota/task/${taskId}/terminate`);
}
async function getOtaTaskDevices(taskId: number, params: Record<string, any>) {
  return requestClient.get<{ items: OtaApi.OtaTaskDevice[]; total: number }>(
    `/ota/task/${taskId}/devices`, { params });
}
async function retryOtaTaskDevice(id: number) {
  return requestClient.post(`/ota/task/device/${id}/retry`);
}
export {
  addOtaTaskDevices, createOtaTask, deleteOtaPackage, getOtaCandidates,
  getOtaPackages, getOtaTaskDevices, getOtaTasks, pauseOtaTask,
  resumeOtaTask, retryOtaTaskDevice, startOtaTask, terminateOtaTask,
  uploadOtaPackage,
};
