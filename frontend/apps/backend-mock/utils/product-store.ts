import { faker } from '@faker-js/faker';

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

/** 产品来源：标准产品（官方/行业模板） / 自定义产品（自建） */
export type ProductSource = 'standard' | 'custom';

const PRODUCT_CATEGORIES = [
  '温湿度传感器',
  '智能插座',
  '智能门锁',
  '光照传感器',
  '空气质量监测',
  '智能照明',
  '网关设备',
  '电表计量',
  '水浸探测',
  '红外遥控',
];

const PRODUCT_PROTOCOLS = ['MQTT', 'CoAP', 'HTTP', 'Modbus'];

const PRODUCT_REMARKS = [
  '官方模板，导入即用',
  '行业通用规范建模',
  '含完整物模型定义',
  '支持批量设备接入',
  '预置告警规则',
];

function fakeProductKey(): string {
  return faker.string.alphanumeric({ casing: 'mixed', length: 11 });
}

function generateProducts(count: number, source: ProductSource): SystemRecord[] {
  const dataList: SystemRecord[] = [];
  for (let i = 0; i < count; i++) {
    dataList.push({
      id: faker.string.uuid(),
      name: `${faker.helpers.arrayElement(PRODUCT_CATEGORIES)}-${faker.string.alphanumeric({ casing: 'upper', length: 4 })}`,
      productKey: fakeProductKey(),
      category: faker.helpers.arrayElement(PRODUCT_CATEGORIES),
      protocol: faker.helpers.arrayElement(PRODUCT_PROTOCOLS),
      status: faker.helpers.arrayElement([0, 1]),
      deviceCount: faker.number.int({ max: 500, min: 0 }),
      source,
      remark: faker.helpers.arrayElement(PRODUCT_REMARKS),
      createTime: formatterCN.format(
        faker.date.between({ from: '2023-01-01', to: '2025-12-31' }),
      ),
    });
  }
  return dataList;
}

const THING_MODEL_DATA_TYPES = [
  'int',
  'double',
  'string',
  'bool',
  'enum',
  'struct',
];

/** 三要素功能名称池 */
const PROPERTY_NAMES = [
  '当前温度',
  '相对湿度',
  '开关状态',
  '工作电流',
  '电池电量',
  '信号强度',
  '累计电量',
  '光照强度',
  'CO2 浓度',
  'PM2.5 浓度',
];
const METHOD_NAMES = [
  '远程重启',
  '恢复出厂',
  '校准时间',
  '固件升级',
  '开关切换',
  '阈值设置',
];
const EVENT_NAMES = [
  '高温告警',
  '低电量告警',
  '故障上报',
  '离线事件',
  '阈值越限',
];

const IDENTIFIER_POOL = [
  'Temperature',
  'Humidity',
  'SwitchStatus',
  'CurrentThreshold',
  'BatteryLevel',
  'SignalStrength',
  'PowerConsumption',
  'Illuminance',
  'RemoteReboot',
  'FactoryReset',
  'SyncTime',
  'FirmwareUpgrade',
  'HighTempAlarm',
  'LowBatteryAlarm',
  'FaultReport',
  'OfflineEvent',
];

function fakeIdentifier(): string {
  return faker.helpers
    .arrayElement(IDENTIFIER_POOL)
    .toLowerCase();
}

/**
 * 一个物模型 = 某产品的一份模型定义，内含 属性 / 方法 / 事件 三要素各若干条。
 * - 属性：{ name, identifier, category, dataType, accessMode, min, max, unit }
 * - 方法：{ name, identifier, callType, inputParams[], outputParam }
 * - 事件：{ name, identifier, level, outputParams[] }
 */
function generateThingModels(count: number): SystemRecord[] {
  const dataList: SystemRecord[] = [];
  for (let i = 0; i < count; i++) {
    const product = faker.helpers.arrayElement(productStore.list);
    const num = () => faker.number.int({ max: 100, min: 0 });

    const properties = faker.helpers
      .arrayElements(PROPERTY_NAMES, { min: 1, max: 5 })
      .map((name) => {
        const dataType = faker.helpers.arrayElement(THING_MODEL_DATA_TYPES);
        const base = {
          name,
          identifier: fakeIdentifier(),
          category: faker.helpers.arrayElement([
            '基础属性',
            '运行状态',
            '故障诊断',
            '能耗统计',
            '环境监测',
          ]),
          dataType,
          accessMode: faker.helpers.arrayElement(['r', 'rw']),
          remark: faker.lorem.sentence(),
        };
        // 每种数据类型带各自的约束结构
        switch (dataType) {
          case 'bool': {
            return { ...base, enumItems: [{ value: 0, description: '关闭' }, { value: 1, description: '打开' }] };
          }
          case 'double':
          case 'int': {
            return {
              ...base,
              min: 0,
              max: num(),
              step: 1,
              unit: faker.helpers.arrayElement(['℃', '%', 'V', 'A', 'kWh', '']),
            };
          }
          case 'enum': {
            return {
              ...base,
              enumItems: [
                { value: '0', description: '低档位' },
                { value: '1', description: '高档位' },
              ],
            };
          }
          case 'string': {
            return { ...base, maxLength: 255 };
          }
          default: {
            // struct = JSON 对象：子字段自身也可能是 struct（嵌套）或带枚举约束
            return {
              ...base,
              params: [
                {
                  name: '对象字段',
                  identifier: fakeIdentifier(),
                  dataType: faker.helpers.arrayElement(THING_MODEL_DATA_TYPES),
                  description: faker.lorem.sentence(),
                },
                {
                  name: '嵌套对象',
                  identifier: fakeIdentifier(),
                  dataType: 'struct',
                  description: faker.lorem.sentence(),
                  params: [
                    {
                      name: '嵌套字段',
                      identifier: fakeIdentifier(),
                      dataType: faker.helpers.arrayElement(THING_MODEL_DATA_TYPES),
                      description: faker.lorem.sentence(),
                    },
                  ],
                },
              ],
            };
          }
        }
      });

    const makeParam = () => {
      const dataType = faker.helpers.arrayElement(THING_MODEL_DATA_TYPES);
      const base = {
        name: faker.helpers.arrayElement(['输入参数', '控制量', '目标值', '模式']),
        identifier: fakeIdentifier(),
        dataType,
        description: faker.lorem.sentence(),
      };
      // enum/bool 参数带枚举项
      if (dataType === 'enum') {
        return {
          ...base,
          enumItems: [
            { value: '0', description: '低档位' },
            { value: '1', description: '高档位' },
          ],
        };
      }
      if (dataType === 'bool') {
        return {
          ...base,
          enumItems: [
            { value: 0, description: '关闭' },
            { value: 1, description: '打开' },
          ],
        };
      }
      return base;
    };

    const methods = faker.helpers
      .arrayElements(METHOD_NAMES, { min: 1, max: 4 })
      .map((name) => ({
        name,
        identifier: fakeIdentifier(),
        callType: faker.helpers.arrayElement(['sync', 'async']),
        inputParams: faker.helpers.arrayElements([makeParam(), makeParam()], {
          min: 0,
          max: 2,
        }),
        outputParam:
          faker.datatype.boolean() ? makeParam() : null,
      }));

    const events = faker.helpers
      .arrayElements(EVENT_NAMES, { min: 1, max: 4 })
      .map((name) => ({
        name,
        identifier: fakeIdentifier(),
        level: faker.helpers.arrayElement(['info', 'warn', 'error']),
        outputParams: faker.helpers.arrayElements([makeParam(), makeParam()], {
          min: 1,
          max: 2,
        }),
      }));

    dataList.push({
      id: faker.string.uuid(),
      productId: product.id,
      productName: product.name,
      version: `v${faker.number.int({ max: 3, min: 1 })}.${faker.number.int({ max: 9, min: 0 })}`,
      status: faker.helpers.arrayElement([0, 1]),
      properties,
      methods,
      events,
      remark: faker.lorem.sentence(),
      createTime: formatterCN.format(
        faker.date.between({ from: '2023-06-01', to: '2025-12-31' }),
      ),
    });
  }
  return dataList;
}

/**
 * 模块级内存存储：
 * - 进程存活期间 CRUD 操作共享同一份数据；
 * - 进程重启后恢复为初始 mock 数据（mock 语义，非持久化）。
 */
export const productStore = {
  list: [
    ...generateProducts(36, 'standard'),
    ...generateProducts(21, 'custom'),
  ],
};

export const thingModelStore = { list: generateThingModels(28) };

export function formatNow(): string {
  return formatterCN.format(new Date());
}
