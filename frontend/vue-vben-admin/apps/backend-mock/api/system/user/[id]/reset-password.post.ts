import { eventHandler, getRouterParam, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { formatNow, systemUserStore } from '~/utils/system-store';

/** 管理员重置用户密码（等保三级：密码可由管理员强制重置）。 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);
  const id = getRouterParam(event, 'id');
  const body = await readBody<Record<string, any>>(event);
  const record = systemUserStore.list.find((item) => item.id === id);
  if (!record) {
    return useResponseError('用户不存在');
  }
  const newPassword = String(body?.newPassword ?? '');
  if (newPassword.length < 8) {
    return useResponseError(
      'BadRequestException',
      '密码复杂度不满足要求：长度至少 8 位',
    );
  }
  record.password = newPassword;
  record.passwordUpdateTime = formatNow();
  return useResponseSuccess(null);
});
