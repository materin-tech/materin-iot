import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { MOCK_USERS } from '~/utils/mock-data';

/** 当前用户修改本人密码（等保三级：验证原密码 + 新密码复杂度）。 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);
  const { oldPassword, newPassword } = await readBody(event);
  const user = MOCK_USERS.find((item) => item.username === userinfo.username);
  if (!user) {
    return useResponseError('用户不存在');
  }
  if (user.password !== oldPassword) {
    return useResponseError('BadRequestException', '原密码错误');
  }
  if (newPassword === oldPassword) {
    return useResponseError('BadRequestException', '新密码不能与原密码相同');
  }
  if (
    newPassword.length < 8 ||
    !/[A-Z]/.test(newPassword) ||
    !/[a-z]/.test(newPassword) ||
    !/\d/.test(newPassword) ||
    !/[!@#$%^&*()_+\-=[\]{}|;:,.<>?/~]/.test(newPassword)
  ) {
    return useResponseError(
      'BadRequestException',
      '密码复杂度不满足要求：至少 8 位，需包含大小写字母、数字和特殊字符',
    );
  }
  user.password = newPassword;
  user.passwordUpdateTime = new Date().toISOString();
  return useResponseSuccess(null);
});
