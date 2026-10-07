import { faker } from '@faker-js/faker';
import { eventHandler, readBody } from 'h3';
import { verifyAccessToken } from '~/utils/jwt-utils';
import {
  sleep,
  unAuthorizedResponse,
  useResponseError,
  useResponseSuccess,
} from '~/utils/response';
import { formatNow, systemUserStore } from '~/utils/system-store';

/** 创建用户（等保三级：必须设置符合复杂度要求的密码）。 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  await sleep(300);

  const body = await readBody<Record<string, any>>(event);
  const name = String(body?.name ?? '').trim();
  if (!name) {
    return useResponseError('BadRequestException', '用户名不能为空');
  }
  const password = String(body?.password ?? '');
  if (password.length < 8) {
    return useResponseError(
      'BadRequestException',
      '必须为新建用户设置密码（至少 8 位，含大小写字母/数字/特殊字符）',
    );
  }
  if (systemUserStore.list.some((item) => item.name === name)) {
    return useResponseError('BadRequestException', '用户名已存在');
  }

  const record: Record<string, any> = {
    id: faker.string.uuid(),
    name,
    password,
    status: body?.status ?? 1,
    orgId: body?.orgId ?? null,
    remark: body?.remark ?? '',
    phone: body?.phone ?? '',
    idType: body?.idType ?? '',
    idNo: maskIdNo(body?.idNo),
    passwordUpdateTime: formatNow(),
    createTime: formatNow(),
  };
  systemUserStore.list.unshift(record);
  return useResponseSuccess(record);
});

function maskIdNo(idNo?: string): string {
  if (!idNo) return '';
  const value = String(idNo);
  if (value.length <= 7) return value;
  return `${value.slice(0, 3)}${'*'.repeat(value.length - 7)}${value.slice(-4)}`;
}
