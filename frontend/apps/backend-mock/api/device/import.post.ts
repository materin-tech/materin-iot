import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { deviceStore, formatNow } from '~/utils/device-store';

/** 批量导入设备：DeviceKey 冲突跳过计为失败 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<{ items?: any[] }>(event);
  const items = Array.isArray(body?.items) ? body.items : [];
  if (items.length === 0) {
    return useResponseError('导入数据为空');
  }

  let imported = 0;
  let failed = 0;
  for (const item of items) {
    if (!item?.name || !item?.productId) {
      failed++;
      continue;
    }
    if (
      item.deviceKey &&
      deviceStore.list.some((d) => d.deviceKey === item.deviceKey)
    ) {
      failed++;
      continue;
    }
    deviceStore.list.unshift({
      status: 0,
      firmware: 'v1.0',
      ...item,
      deviceKey: item.deviceKey || crypto.randomUUID().replaceAll('-', '').slice(0, 12),
      id: crypto.randomUUID(),
      createTime: formatNow(),
    });
    imported++;
  }
  return useResponseSuccess({ imported, failed });
});
