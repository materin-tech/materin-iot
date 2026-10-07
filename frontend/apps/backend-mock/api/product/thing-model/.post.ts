import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { formatNow, productStore, thingModelStore } from '~/utils/product-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<Record<string, any>>(event);
  if (!body?.productId) {
    return useResponseError('所属产品不能为空');
  }

  // 由 productId 解析产品名称（维护冗余字段，列表展示用）
  const product = productStore.list.find((item) => item.id === body.productId);

  const record = {
    status: 0,
    version: 'v1.0',
    properties: [],
    methods: [],
    events: [],
    ...body,
    productName: product?.name ?? '',
    id: crypto.randomUUID(),
    createTime: formatNow(),
  };
  thingModelStore.list.unshift(record);
  return useResponseSuccess(record);
});
