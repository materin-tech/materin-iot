import { faker } from '@faker-js/faker';
import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';

/** 创建组织节点。 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<Record<string, any>>(event);
  const name = String(body?.name ?? '').trim();
  if (!name) {
    return useResponseError('BadRequestException', '组织名不能为空');
  }
  return useResponseSuccess({
    id: faker.string.uuid(),
    pid: body?.pid ?? 0,
    name,
    status: body?.status ?? 1,
    remark: body?.remark ?? '',
    children: [],
  });
});
