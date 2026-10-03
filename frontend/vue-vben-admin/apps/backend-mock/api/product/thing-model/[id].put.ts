import { eventHandler, getRouterParam, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { productStore, thingModelStore } from '~/utils/product-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const id = getRouterParam(event, 'id');
  const body = await readBody<Record<string, any>>(event);
  const record = thingModelStore.list.find((item) => item.id === id);
  if (!record) {
    return useResponseError('物模型不存在');
  }
  Object.assign(record, body);
  // productId 变更时同步刷新冗余的产品名称
  if (body?.productId) {
    const product = productStore.list.find(
      (item) => item.id === record.productId,
    );
    record.productName = product?.name ?? '';
  }
  return useResponseSuccess(record);
});
