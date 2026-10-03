import { eventHandler, getRouterParam, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { deviceAlertStore } from '~/utils/device-store';

/** 告警处理：主要供「标记已处理」使用 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(200);

  const id = getRouterParam(event, 'id');
  const body = await readBody<Record<string, any>>(event);
  const record = deviceAlertStore.list.find((item) => item.id === id);
  if (!record) {
    return useResponseError('告警不存在');
  }
  Object.assign(record, body);
  return useResponseSuccess(record);
});
