import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { formatNow, productStore } from '~/utils/product-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<Record<string, any>>(event);
  if (!body?.name) {
    return useResponseError('产品名称不能为空');
  }

  const record = {
    status: 0,
    deviceCount: 0,
    ...body,
    // 兜底：未指定来源时归入自定义产品
    source: body.source ?? 'custom',
    id: crypto.randomUUID(),
    createTime: formatNow(),
  };
  productStore.list.unshift(record);
  return useResponseSuccess(record);
});
