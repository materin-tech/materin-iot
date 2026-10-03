import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { deviceStore, formatNow } from '~/utils/device-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<Record<string, any>>(event);
  if (!body?.name) {
    return useResponseError('设备名称不能为空');
  }
  if (!body?.productId) {
    return useResponseError('所属产品不能为空');
  }

  const record = {
    status: 0,
    firmware: 'v1.0',
    ...body,
    deviceKey: body.deviceKey || crypto.randomUUID().replaceAll('-', '').slice(0, 12),
    id: crypto.randomUUID(),
    createTime: formatNow(),
  };
  deviceStore.list.unshift(record);
  return useResponseSuccess(record);
});
