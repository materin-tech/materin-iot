<script setup lang="ts">
import type { Recordable } from '@vben/types';

import type { VbenFormSchema } from '#/adapter/form';

import { computed } from 'vue';

import { ProfilePasswordSetting, z } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { changeOwnPassword } from '#/api/core/password';

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'oldPassword',
      label: '旧密码',
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: '请输入旧密码',
      },
    },
    {
      fieldName: 'newPassword',
      label: '新密码',
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: '请输入新密码',
      },
      // 与 system/user/data.ts 相同的复杂度规则：8 位以上 + 大写 + 小写 + 数字 + 特殊字符
      rules: z
        .string({ error: '请输入新密码' })
        .min(8, { message: '密码至少 8 位' })
        .regex(/[A-Z]/, { message: '密码需包含大写字母' })
        .regex(/[a-z]/, { message: '密码需包含小写字母' })
        .regex(/\d/, { message: '密码需包含数字' })
        .regex(/[^A-Za-z0-9]/, { message: '密码需包含特殊字符' }),
    },
    {
      fieldName: 'confirmPassword',
      label: '确认密码',
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: '请再次输入新密码',
      },
      dependencies: {
        rules(values) {
          const { newPassword } = values;
          return z
            .string({ error: '请再次输入新密码' })
            .min(1, { message: '请再次输入新密码' })
            .refine((value) => value === newPassword, {
              message: '两次输入的密码不一致',
            });
        },
        triggerFields: ['newPassword'],
      },
    },
  ];
});

async function handleSubmit(values: Recordable<any>) {
  await changeOwnPassword(values.oldPassword, values.newPassword);
  message.success('密码修改成功');
}
</script>
<template>
  <ProfilePasswordSetting
    class="w-1/3"
    :form-schema="formSchema"
    @submit="handleSubmit"
  />
</template>
