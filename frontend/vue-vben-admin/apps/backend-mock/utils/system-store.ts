import { faker } from '@faker-js/faker';

import { getMenuIds, MOCK_MENU_LIST } from './mock-data';

const formatterCN = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

export type SystemRecord = Record<string, any> & { id: string };

/**
 * 模块级内存存储：
 * - 进程存活期间 CRUD 操作共享同一份数据；
 * - 进程重启后恢复为初始 mock 数据（mock 语义，非持久化）。
 */

function generateUsers(count: number): SystemRecord[] {
  const dataList: SystemRecord[] = [];
  for (let i = 0; i < count; i++) {
    dataList.push({
      id: faker.string.uuid(),
      name: faker.commerce.product(),
      status: faker.helpers.arrayElement([0, 1]),
      createTime: formatterCN.format(
        faker.date.between({ from: '2022-01-01', to: '2025-01-01' }),
      ),
      deptId: faker.string.uuid(),
      remark: faker.lorem.sentence(),
    });
  }
  return dataList;
}

function generateRoles(count: number): SystemRecord[] {
  const menuIds = getMenuIds(MOCK_MENU_LIST);
  const dataList: SystemRecord[] = [];
  for (let i = 0; i < count; i++) {
    dataList.push({
      id: faker.string.uuid(),
      name: faker.commerce.product(),
      status: faker.helpers.arrayElement([0, 1]),
      createTime: formatterCN.format(
        faker.date.between({ from: '2022-01-01', to: '2025-01-01' }),
      ),
      permissions: faker.helpers.arrayElements(menuIds),
      remark: faker.lorem.sentence(),
    });
  }
  return dataList;
}

export const systemUserStore = { list: generateUsers(100) };

export const systemRoleStore = { list: generateRoles(100) };

/** 菜单为树形结构，深拷贝一份用于可变 CRUD */
export const systemMenuStore = {
  list: structuredClone(MOCK_MENU_LIST) as Record<string, any>[],
};

export function formatNow(): string {
  return formatterCN.format(new Date());
}

/** 在菜单树中查找节点 */
export function findMenuById(
  list: Record<string, any>[],
  id: string | number,
): Record<string, any> | undefined {
  for (const node of list) {
    if (String(node.id) === String(id)) return node;
    if (node.children?.length) {
      const found = findMenuById(node.children, id);
      if (found) return found;
    }
  }
  return undefined;
}

/** 从菜单树中移除节点（含其子树），返回是否移除成功 */
export function removeMenuById(
  list: Record<string, any>[],
  id: string | number,
): boolean {
  const index = list.findIndex((node) => String(node.id) === String(id));
  if (index !== -1) {
    list.splice(index, 1);
    return true;
  }
  return list.some((node) => {
    if (node.children?.length) return removeMenuById(node.children, id);
    return false;
  });
}

/** 生成一个新的菜单 ID（现有最大数字 ID + 1） */
export function nextMenuId(): number {
  const ids: number[] = [];
  const walk = (nodes: Record<string, any>[]) => {
    nodes.forEach((node) => {
      const id = Number(node.id);
      if (!Number.isNaN(id)) ids.push(id);
      if (node.children?.length) walk(node.children);
    });
  };
  walk(systemMenuStore.list);
  return ids.length > 0 ? Math.max(...ids) + 1 : 1;
}
