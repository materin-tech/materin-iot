import { eventHandler, getRouterParam } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseSuccess,
} from '~/utils/response';
import { systemUserStore } from '~/utils/system-store';

/** 解锁账号（等保三级：锁定后可由管理员手动解锁）。 */
export default eventHandler(async (event) {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);
  const id = getRouterParam(event, 'id');
  const record = systemUserStore.list.find((item) => item.id === id);
  if (!record) {
    return useResponseError('用户不存在');
  }
  return useResponseSuccess(null);
});
