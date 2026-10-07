import { eventHandler } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import { unAuthorizedResponse, useResponseSuccess } from '~/utils/response';
import { systemSecurityConfigStore } from '~/utils/system-store';

/** 等保三级安全参数（存 sys_config 语义，mock 存内存 store）。 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  return useResponseSuccess({ ...systemSecurityConfigStore });
});
