import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';

import { $t } from '#/locales';

/** 接入协议可选项 */
export const PROTOCOL_OPTIONS = ['MQTT', 'CoAP', 'HTTP', 'Modbus'].map(
  (value) => ({ label: value, value }),
);

export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('product.name'),
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'category',
      label: $t('product.category'),
      rules: 'required',
    },
    {
      component: 'Input',
      componentProps: {
        allowClear: true,
        maxLength: 11,
        placeholder: $t('product.productKeyPlaceholder'),
      },
      fieldName: 'productKey',
      label: $t('product.productKey'),
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        class: 'w-full',
        options: PROTOCOL_OPTIONS,
        placeholder: $t('product.protocol'),
      },
      fieldName: 'protocol',
      label: $t('product.protocol'),
    },
    {
      component: 'RadioGroup',
      componentProps: {
        buttonStyle: 'solid',
        options: [
          { label: $t('product.statusDraft'), value: 0 },
          { label: $t('product.statusPublished'), value: 1 },
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

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('product.name'),
    },
    {
      component: 'Input',
      fieldName: 'productKey',
      label: $t('product.productKey'),
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('product.statusDraft'), value: 0 },
          { label: $t('product.statusPublished'), value: 1 },
        ],
      },
      fieldName: 'status',
      label: $t('product.status'),
    },
    {
      component: 'RangePicker',
      fieldName: 'createTime',
      label: $t('product.createTime'),
    },
  ];
}

export function useColumns(): VxeTableGridColumns {
  return [
    {
      type: 'checkbox',
      fixed: 'left',
      width: 50,
    },
    {
      field: 'name',
      fixed: 'left',
      minWidth: 180,
      title: $t('product.name'),
    },
    {
      field: 'productKey',
      title: $t('product.productKey'),
      width: 140,
    },
    {
      field: 'category',
      title: $t('product.category'),
      width: 140,
    },
    {
      field: 'protocol',
      title: $t('product.protocol'),
      width: 100,
    },
    {
      field: 'deviceCount',
      sortable: true,
      title: $t('product.deviceCount'),
      width: 100,
    },
    {
      cellRender: {
        name: 'CellTag',
        options: [
          { color: 'processing', label: $t('product.statusDraft'), value: 0 },
          { color: 'success', label: $t('product.statusPublished'), value: 1 },
        ],
      },
      field: 'status',
      title: $t('product.status'),
      width: 100,
    },
    {
      field: 'createTime',
      title: $t('product.createTime'),
      width: 170,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      slots: { default: 'action' },
      title: $t('product.operation'),
      width: 160,
    },
  ];
}
