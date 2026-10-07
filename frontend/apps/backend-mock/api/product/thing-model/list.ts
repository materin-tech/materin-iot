import { eventHandler, getQuery } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import { unAuthorizedResponse, usePageResponseSuccess } from '~/utils/response';
import { thingModelStore } from '~/utils/product-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }

  const { page = 1, pageSize = 12, keyword, status } = getQuery(event);
  let listData = structuredClone(thingModelStore.list);
  if (keyword) {
    const kw = String(keyword).toLowerCase();
    listData = listData.filter(
      (item) =>
        item.productName?.toLowerCase().includes(kw) ||
        item.version?.toLowerCase().includes(kw),
    );
  }
  if (['0', '1'].includes(status as string)) {
    listData = listData.filter((item) => item.status === Number(status));
  }
  return usePageResponseSuccess(page as string, pageSize as string, listData);
});
