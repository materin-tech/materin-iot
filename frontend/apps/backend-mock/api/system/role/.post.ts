import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import {
  formatNow,
  systemRoleStore,
  type SystemRecord,
} from '~/utils/system-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<Record<string, any>>(event);
  if (!body?.name) {
    return useResponseError('角色名不能为空');
  }

  const record: SystemRecord = {
    status: 1,
    permissions: [],
    ...body,
    id: crypto.randomUUID(),
    createTime: formatNow(),
  };
  systemRoleStore.list.unshift(record);
  return useResponseSuccess(record);
});
