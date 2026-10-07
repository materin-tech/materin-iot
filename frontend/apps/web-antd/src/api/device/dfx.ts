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
    intervalSeconds?: number;
    enabled?: number;
    remark?: string;
  }
  export interface DfxRule {
    id?: number;
    deviceId: number;
    deviceName?: string;
    metric: string;
    comparator: string;
    threshold: number;
    level?: string;
    suppressSeconds?: number;
    enabled?: number;
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
async function getDfxHealth() {
  return requestClient.get<DfxApi.DfxHealthResponse>('/device/dfx/health');
}
export { getDfxHealth, createDfxRule, createDfxTarget, deleteDfxRule, deleteDfxTarget,
  getDfxHistory, getDfxLatest, getDfxRuleList, getDfxTargetList,
};
