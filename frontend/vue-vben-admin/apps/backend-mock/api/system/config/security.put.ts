import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseSuccess,
} from '~/utils/response';
import { systemSecurityConfigStore } from '~/utils/system-store';

/** 更新等保三级安全参数。 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);
  const body = await readBody<Record<string, any>>(event);
  Object.assign(systemSecurityConfigStore, body);
  return useResponseSuccess({ ...systemSecurityConfigStore });
});
