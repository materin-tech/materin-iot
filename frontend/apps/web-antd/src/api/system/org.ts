import { requestClient } from '#/api/request';

export namespace SystemOrgApi {
  export interface SystemOrg {
    [key: string]: any;
    children?: SystemOrg[];
    id: string;
    name: string;
    remark?: string;
    status: 0 | 1;
  }
}

/**
 * 获取组织列表数据
 */
async function getOrgList() {
  return requestClient.get<Array<SystemOrgApi.SystemOrg>>(
    '/system/org/list',
  );
}

/**
 * 创建部门
 * @param data 组织数据
 */
async function createOrg(
  data: Omit<SystemOrgApi.SystemOrg, 'children' | 'id'>,
) {
  return requestClient.post('/system/org', data);
}

/**
 * 更新部门
 *
 * @param id 部门 ID
 * @param data 组织数据
 */
async function updateOrg(
  id: string,
  data: Omit<SystemOrgApi.SystemOrg, 'children' | 'id'>,
) {
  return requestClient.put(`/system/org/${id}`, data);
}

/**
 * 删除组织
 * @param id 部门 ID
 */
async function deleteOrg(id: string) {
  return requestClient.delete(`/system/org/${id}`);
}

export { createOrg, deleteOrg, getOrgList, updateOrg };
