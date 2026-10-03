import { eventHandler, getRouterParam } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { thingModelStore } from '~/utils/product-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const id = getRouterParam(event, 'id');
  const index = thingModelStore.list.findIndex((item) => item.id === id);
  if (index === -1) {
    return useResponseError('物模型不存在');
  }
  thingModelStore.list.splice(index, 1);
  return useResponseSuccess(true);
});
