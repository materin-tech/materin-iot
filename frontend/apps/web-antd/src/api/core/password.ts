import { requestClient } from '#/api/request';

/**
 * 修改本人密码（等保三级：验证原密码，新密码满足复杂度）
 */
export async function changeOwnPassword(oldPassword: string, newPassword: string) {
  return requestClient.post('/user/password', { oldPassword, newPassword });
}
