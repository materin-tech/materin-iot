import type { VbenFormSchema } from '#/adapter/form';

import { $t } from '#/locales';
import { getProductList } from '#/api/product/product';

/** 三要素定义：属性 / 方法 / 事件 */
export const THING_MODEL_SECTIONS = [
  {
    icon: 'icon-[lucide--sliders-horizontal]',
    itemsKey: 'properties',
    label: $t('product.thingModel.typeProperty'),
    value: 'property',
  },
  {
    icon: 'icon-[lucide--wrench]',
    itemsKey: 'methods',
    label: $t('product.thingModel.typeService'),
    value: 'service',
  },
  {
    icon: 'icon-[lucide--zap]',
    label: $t('product.thingModel.typeEvent'),
    itemsKey: 'events',
    value: 'event',
  },
] as const;

export type SectionKey = (typeof THING_MODEL_SECTIONS)[number]['itemsKey'];

/** 该条目是否需要数据类型字段（属性/方法需要，事件不需要） */
export function sectionHasDataType(key: SectionKey): boolean {
  return key !== 'events';
}

/** 获取所属产品下拉选项（mock 数据量小，一次取全） */
export async function loadProductOptions() {
  const res = await getProductList({ page: 1, pageSize: 200 });
  return res.items.map((item) => ({ label: item.name, value: item.id }));
}

export const DATA_TYPE_OPTIONS = [
  'bool',
  'double',
  'enum',
  'int',
  'string',
  'struct',
].map((value) => ({ label: value, value }));

/** 属性分类选项 */
export const CATEGORY_OPTIONS = [
  '基础属性',
  '运行状态',
  '故障诊断',
  '能耗统计',
  '环境监测',
].map((value) => ({ label: value, value }));

/** 读写类型 */
export const ACCESS_MODE_OPTIONS = [
  { label: $t('product.thingModel.modeRw'), value: 'rw' },
  { label: $t('product.thingModel.modeR'), value: 'r' },
] as const;

/** 方法调用方式 */
export const CALL_TYPE_OPTIONS = [
  { label: $t('product.thingModel.callSync'), value: 'sync' },
  { label: $t('product.thingModel.callAsync'), value: 'async' },
] as const;

/** 事件级别 */
export const EVENT_LEVEL_OPTIONS = [
  { label: $t('product.thingModel.levelInfo'), value: 'info' },
  { label: $t('product.thingModel.levelWarn'), value: 'warn' },
  { label: $t('product.thingModel.levelError'), value: 'error' },
] as const;

/** 该数据类型是否支持数值区间约束 */
export function supportsRange(dataType?: string): boolean {
  return ['double', 'int'].includes(dataType ?? '');
}

/** 弹窗上部：物模型基础信息（三要素内容在 Tabs 中维护，不走表单字段） */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: loadProductOptions,
        class: 'w-full',
        showSearch: true,
        optionFilterProp: 'label',
      },
      fieldName: 'productId',
      label: $t('product.thingModel.productId'),
      rules: 'required',
    },
    {
      component: 'Input',
      componentProps: {
        placeholder: 'v1.0',
      },
      defaultValue: 'v1.0',
      fieldName: 'version',
      label: $t('product.thingModel.version'),
    },
    {
      component: 'RadioGroup',
      componentProps: {
        buttonStyle: 'solid',
        options: [
          { label: $t('product.thingModel.statusDraft'), value: 0 },
          { label: $t('product.thingModel.statusPublished'), value: 1 },
        ],
        optionType: 'button',
      },
      defaultValue: 0,
      fieldName: 'status',
      label: $t('product.status'),
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: $t('product.remark'),
    },
  ];
}
