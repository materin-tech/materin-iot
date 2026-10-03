import { eventHandler, getQuery } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import { unAuthorizedResponse, usePageResponseSuccess } from '~/utils/response';
import { deviceStore } from '~/utils/device-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }

  const { page = 1, pageSize = 20, name, deviceKey, productId, status } =
    getQuery(event);
  let listData = structuredClone(deviceStore.list);
  if (productId) {
    listData = listData.filter((item) => item.productId === productId);
  }
  if (name) {
    listData = listData.filter((item) =>
      item.name.toLowerCase().includes(String(name).toLowerCase()),
    );
  }
  if (deviceKey) {
    listData = listData.filter((item) =>
      item.deviceKey.toLowerCase().includes(String(deviceKey).toLowerCase()),
    );
  }
  if (['0', '1', '2'].includes(status as string)) {
    listData = listData.filter((item) => item.status === Number(status));
  }
  return usePageResponseSuccess(page as string, pageSize as string, listData);
});
