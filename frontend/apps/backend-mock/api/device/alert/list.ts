import { eventHandler, getQuery } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import { unAuthorizedResponse, usePageResponseSuccess } from '~/utils/response';
import { deviceAlertStore } from '~/utils/device-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }

  const { page = 1, pageSize = 20, keyword, level, status } = getQuery(event);
  let listData = structuredClone(deviceAlertStore.list);
  if (keyword) {
    const kw = String(keyword).toLowerCase();
    listData = listData.filter(
      (item) =>
        item.deviceName.toLowerCase().includes(kw) ||
        item.content.toLowerCase().includes(kw),
    );
  }
  if (['info', 'warn', 'error'].includes(level as string)) {
    listData = listData.filter((item) => item.level === level);
  }
  if (['0', '1'].includes(status as string)) {
    listData = listData.filter((item) => item.status === Number(status));
  }
  return usePageResponseSuccess(page as string, pageSize as string, listData);
});
