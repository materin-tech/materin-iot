import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import {
  findMenuById,
  formatNow,
  nextMenuId,
  removeMenuById,
  systemMenuStore,
} from '~/utils/system-store';

export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<Record<string, any>>(event);
  if (!body?.name || !body?.path) {
    return useResponseError('菜单名称与路由地址不能为空');
  }

  const record: Record<string, any> = {
    status: 1,
    ...body,
    id: nextMenuId(),
  };
  record.createTime = formatNow();

  const pid = record.pid ? String(record.pid) : '';
  if (pid) {
    const parent = findMenuById(systemMenuStore.list, pid);
    if (!parent) {
      return useResponseError('上级菜单不存在');
    }
    parent.children = parent.children ?? [];
    parent.children.push(record);
  } else {
    systemMenuStore.list.push(record);
  }
  return useResponseSuccess(record);
});
