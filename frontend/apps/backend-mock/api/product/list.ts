import { eventHandler, getQuery } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import { unAuthorizedResponse, usePageResponseSuccess } from '~/utils/response';
import { productStore } from '~/utils/product-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }

  const {
    page = 1,
    pageSize = 20,
    name,
    productKey,
    source,
    status,
    startTime,
    endTime,
  } = getQuery(event);
  let listData = structuredClone(productStore.list);
  if (source) {
    listData = listData.filter((item) => item.source === source);
  }
  if (name) {
    listData = listData.filter((item) =>
      item.name.toLowerCase().includes(String(name).toLowerCase()),
    );
  }
  if (productKey) {
    listData = listData.filter((item) =>
      item.productKey
        .toLowerCase()
        .includes(String(productKey).toLowerCase()),
    );
  }
  if (['0', '1'].includes(status as string)) {
    listData = listData.filter((item) => item.status === Number(status));
  }
  if (startTime) {
    listData = listData.filter((item) => item.createTime >= startTime);
  }
  if (endTime) {
    listData = listData.filter((item) => item.createTime <= endTime);
  }
  return usePageResponseSuccess(page as string, pageSize as string, listData);
});
