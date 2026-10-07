import type { DescriptionsItemType } from '@vben/common-ui';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { SystemUserApi } from '#/api';

import { h } from 'vue';

import { Tag } from 'ant-design-vue';

import { getOrgList } from '#/api';
import { $t } from '#/locales';
import { z } from '#/adapter/form';

export function useFormSchema(isEdit = false): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.user.name'),
      rules: 'required',
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: $t('system.user.passwordTip'),
      },
      fieldName: 'password',
      label: $t('system.user.password'),
      // 创建时必填且校验复杂度；编辑时留空表示不修改密码
      rules: isEdit
        ? z.string().optional().or(z.literal(''))
        : z
            .string()
            .min(1, { message: $t('system.user.passwordTip') })
            .regex(/[A-Z]/, { message: $t('system.user.passwordTip') })
            .regex(/[a-z]/, { message: $t('system.user.passwordTip') })
            .regex(/\d/, { message: $t('system.user.passwordTip') })
            .regex(/[^A-Za-z0-9]/, {
              message: $t('system.user.passwordTip'),
            }),
    },
    {
      component: 'ApiTreeSelect',
      componentProps: {
        allowClear: true,
        api: getOrgList,
        class: 'w-full',
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
      },
      fieldName: 'orgId',
      label: $t('system.user.org'),
      rules: 'required',
    },
    {
      component: 'Input',
      componentProps: { placeholder: '13xxxxxxxxx' },
      fieldName: 'phone',
      label: $t('system.user.phone'),
      rules: z.string().regex(/^1[3-9]\d{9}$/, { message: $t('system.user.phone') }).optional().or(z.literal('')),
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('system.user.idTypeCard'), value: 'ID_CARD' },
          { label: $t('system.user.idTypePassport'), value: 'PASSPORT' },
          { label: $t('system.user.idTypeOther'), value: 'OTHER' },
        ],
      },
      fieldName: 'idType',
      label: $t('system.user.idType'),
    },
    {
      component: 'Input',
      fieldName: 'idNo',
      label: $t('system.user.idNo'),
      rules: z.string().optional(),
    },
    {
      component: 'RadioGroup',
      componentProps: {
        buttonStyle: 'solid',
        options: [
          { label: $t('common.enabled'), value: 1 },
          { label: $t('common.disabled'), value: 0 },
        ],
        optionType: 'button',
      },
      defaultValue: 1,
      fieldName: 'status',
      label: $t('system.user.status'),
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: $t('system.user.remark'),
    },
  ];
}

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.user.name'),
    },
    { component: 'Input', fieldName: 'id', label: $t('system.user.id') },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('common.enabled'), value: 1 },
          { label: $t('common.disabled'), value: 0 },
        ],
      },
      fieldName: 'status',
      label: $t('system.user.status'),
    },
    {
      component: 'Input',
      fieldName: 'remark',
      label: $t('system.user.remark'),
    },
    {
      component: 'RangePicker',
      fieldName: 'createTime',
      label: $t('system.user.createTime'),
    },
  ];
}

/**
 * 用户详情描述列表项
 * @param row 用户数据
 */
export function useDescriptionItems(
  row?: SystemUserApi.SystemUser,
): DescriptionsItemType[] {
  const enabled = row?.status === 1;
  return [
    { label: $t('system.user.name'), content: row?.name },
    { label: $t('system.user.id'), content: row?.id },
    { label: $t('system.user.org'), content: row?.orgId },
    { label: $t('system.user.phone'), content: row?.phone },
    { label: $t('system.user.idType'), content: row?.idType },
    { label: $t('system.user.idNo'), content: row?.idNo },
    { label: $t('system.user.passwordUpdateTime'), content: row?.passwordUpdateTime },
    {
      label: $t('system.user.status'),
      content: () =>
        h(
          Tag,
          {
            color: enabled ? 'success' : 'error',
          },
          {
            default: () =>
              enabled ? $t('common.enabled') : $t('common.disabled'),
          },
        ),
    },
    { label: $t('system.user.createTime'), content: row?.createTime },
    { label: $t('system.user.remark'), content: row?.remark },
  ];
}

export function useColumns<T = SystemUserApi.SystemUser>(
  onStatusChange?: (newStatus: any, row: T) => PromiseLike<boolean | undefined>,
): VxeTableGridColumns {
  return [
    {
      field: 'name',
      title: $t('system.user.name'),
      width: 200,
    },
    {
      field: 'id',
      title: $t('system.user.id'),
      width: 200,
    },
    {
      field: 'phone',
      title: $t('system.user.phone'),
      width: 140,
    },
    {
      cellRender: {
        attrs: { beforeChange: onStatusChange },
        name: onStatusChange ? 'CellSwitch' : 'CellTag',
      },
      field: 'status',
      title: $t('system.user.status'),
      width: 100,
    },
    {
      field: 'remark',
      minWidth: 100,
      title: $t('system.user.remark'),
    },
    {
      field: 'createTime',
      title: $t('system.user.createTime'),
      width: 200,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      slots: { default: 'action' },
      title: $t('system.user.operation'),
      width: 180,
    },
  ];
}
