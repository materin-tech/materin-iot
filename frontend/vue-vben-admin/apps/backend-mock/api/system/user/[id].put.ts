import { eventHandler, getRouterParam, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { systemUserStore } from '~/utils/system-store';

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
  Object.assign(record, body);
  return useResponseSuccess(record);
});
