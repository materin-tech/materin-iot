import { faker } from '@faker-js/faker';

import { productStore } from './product-store';
import type { SystemRecord } from './system-store';

const formatterCN = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

const REMARKS = [
  '车间一号产线',
  '园区北门',
  '办公楼三层',
  '机房 A 区',
  '室外立杆',
  '仓库东侧',
];

function fakeTime(from: string) {
  return formatterCN.format(
    faker.date.between({ from, to: '2026-10-01' }),
  );
}

/** 设备：0-未激活 1-在线 2-离线 */
function generateDevices(count: number): SystemRecord[] {
  const products = productStore.list;
  const dataList: SystemRecord[] = [];
  for (let i = 0; i < count; i++) {
    const product = faker.helpers.arrayElement(products);
    dataList.push({
      id: faker.string.uuid(),
      name: `${product.name}-设备${faker.string.alphanumeric({ casing: 'upper', length: 4 })}`,
      deviceKey: faker.string.alphanumeric({ casing: 'mixed', length: 12 }),
      productId: product.id,
      productName: product.name,
      status: faker.helpers.weightedArrayElement([
        { value: 1, weight: 6 },
        { value: 2, weight: 3 },
        { value: 0, weight: 1 },
      ]),
      firmware: `v${faker.number.int({ max: 3, min: 1 })}.${faker.number.int({ max: 9, min: 0 })}`,
      lastOnline: fakeTime('2025-10-01'),
      remark: faker.helpers.arrayElement(REMARKS),
      createTime: fakeTime('2023-01-01'),
    });
  }
  return dataList;
}

const ALERT_CONTENTS = [
  '温度超过阈值上限',
  '设备离线超过 30 分钟',
  '电池电量低于 10%',
  '信号强度持续偏低',
  '检测到异常振动',
  '水浸传感器触发',
  '电流超出安全范围',
];

const ALERT_LEVELS = ['info', 'warn', 'error'];

/** 事件告警：0-未处理 1-已处理 */
function generateAlerts(count: number): SystemRecord[] {
  const devices = deviceStore.list;
  const dataList: SystemRecord[] = [];
  for (let i = 0; i < count; i++) {
    const device = faker.helpers.arrayElement(devices);
    dataList.push({
      id: faker.string.uuid(),
      deviceId: device.id,
      deviceName: device.name,
      level: faker.helpers.arrayElement(ALERT_LEVELS),
      content: faker.helpers.arrayElement(ALERT_CONTENTS),
      status: faker.helpers.weightedArrayElement([
        { value: 0, weight: 4 },
        { value: 1, weight: 6 },
      ]),
      createTime: fakeTime('2025-06-01'),
    });
  }
  return dataList;
}

const TRIGGERS = [
  '设备属性上报触发',
  '设备离线触发',
  '定时触发（每小时）',
  '事件告警触发',
];
const ACTIONS = [
  '发送告警通知',
  '执行场景：打开排风',
  '下发设备命令',
  '推送 Webhook',
];

/** 规则联动：0-停用 1-启用 */
function generateRules(count: number): SystemRecord[] {
  const dataList: SystemRecord[] = [];
  for (let i = 0; i < count; i++) {
    dataList.push({
      id: faker.string.uuid(),
      name: `联动规则-${faker.string.alphanumeric({ casing: 'upper', length: 4 })}`,
      trigger: faker.helpers.arrayElement(TRIGGERS),
      action: faker.helpers.arrayElement(ACTIONS),
      status: faker.helpers.weightedArrayElement([
        { value: 1, weight: 7 },
        { value: 0, weight: 3 },
      ]),
      remark: faker.lorem.sentence(),
      createTime: fakeTime('2024-01-01'),
    });
  }
  return dataList;
}

/**
 * 模块级内存存储：进程存活期间共享，重启还原 mock 数据。
 * 注意：告警生成依赖 deviceStore，需在其后初始化。
 */
export const deviceStore = { list: generateDevices(60) };
export const deviceAlertStore = { list: generateAlerts(42) };
export const deviceRuleStore = { list: generateRules(15) };

export function formatNow(): string {
  return formatterCN.format(new Date());
}

export type { SystemRecord };
