import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { deviceRuleStore, formatNow } from '~/utils/device-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<Record<string, any>>(event);
  if (!body?.name) {
    return useResponseError('规则名称不能为空');
  }

  const record = {
    status: 0,
    ...body,
    id: crypto.randomUUID(),
    createTime: formatNow(),
  };
  deviceRuleStore.list.unshift(record);
  return useResponseSuccess(record);
});
