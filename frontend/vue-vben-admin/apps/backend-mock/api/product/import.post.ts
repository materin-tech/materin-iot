import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { formatNow, productStore } from '~/utils/product-store';

/** 批量导入产品（导入端点）：重复 ProductKey 直接跳过计为失败 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<{ items?: any[]; source?: string }>(event);
  const items = Array.isArray(body?.items) ? body.items : [];
  if (items.length === 0) {
    return useResponseError('导入数据为空');
  }

  let imported = 0;
  let failed = 0;
  for (const item of items) {
    if (!item?.name) {
      failed++;
      continue;
    }
    // ProductKey 冲突去重
    if (
      item.productKey &&
      productStore.list.some((p) => p.productKey === item.productKey)
    ) {
      failed++;
      continue;
    }
    productStore.list.unshift({
      status: 0,
      deviceCount: 0,
      ...item,
      // 来源以导入请求指定为准，其次用条目自带，最后归入自定义
      source: body?.source ?? item.source ?? 'custom',
      productKey: item.productKey || crypto.randomUUID().replaceAll('-', '').slice(0, 11),
      id: crypto.randomUUID(),
      createTime: formatNow(),
    });
    imported++;
  }
  return useResponseSuccess({ imported, failed });
});
