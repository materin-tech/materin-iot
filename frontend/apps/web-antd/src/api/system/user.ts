import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace SystemUserApi {
  export interface SystemUser {
    [key: string]: any;
    id: string;
    name: string;
    permissions: string[];
    remark?: string;
    status: 0 | 1;
    /** 实名手机号 */
    phone?: string;
    /** 证件类型：ID_CARD-身份证 PASSPORT-护照 OTHER-其他 */
    idType?: '' | 'ID_CARD' | 'PASSPORT' | 'OTHER';
    /** 证件号（后端脱敏返回，如 110***********1234） */
    idNo?: string;
    /** 密码最后修改时间（空表示首次登录需修改） */
    passwordUpdateTime?: string;
    /** 组织 ID（原部门） */
    orgId?: string;
  }
}

/**
 * 获取用户列表数据
 */
async function getUserList(params: Recordable<any>) {
  return requestClient.get<Array<SystemUserApi.SystemUser>>(
    '/system/user/list',
    { params },
  );
}

/**
 * 创建用户
 * @param data 用户数据
 */
async function createUser(data: Omit<SystemUserApi.SystemUser, 'id'>) {
  return requestClient.post('/system/user', data);
}

/**
 * 更新用户
 *
 * @param id 用户 ID
 * @param data 用户数据
 */
async function updateUser(
  id: string,
  data: Omit<SystemUserApi.SystemUser, 'id'>,
) {
  return requestClient.put(`/system/user/${id}`, data);
}

/**
 * 删除用户
 * @param id 用户 ID
 */
async function deleteUser(id: string) {
  return requestClient.delete(`/system/user/${id}`);
}

/**
 * 管理员重置用户密码（等保三级）
 */
async function resetUserPassword(id: string, newPassword: string) {
  return requestClient.post(`/system/user/${id}/reset-password`, {
    newPassword,
  });
}

/**
 * 解锁账号（等保三级：清空登录失败计数）
 */
async function unlockUser(id: string) {
  return requestClient.post(`/system/user/${id}/unlock`);
}

export { createUser, deleteUser, getUserList, resetUserPassword, unlockUser, updateUser };
