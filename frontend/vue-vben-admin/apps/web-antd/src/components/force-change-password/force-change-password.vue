<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { computed, reactive } from 'vue';

import { Button, Modal, message } from 'ant-design-vue';

import { useVbenForm, z } from '#/adapter/form';
import { changeOwnPassword } from '#/api/core/password';
import { useAuthStore } from '#/store';

const authStore = useAuthStore();

const [PasswordForm, formApi] = useVbenForm(
  reactive({
    schema: computed((): VbenFormSchema[] => [
      {
        component: 'VbenInputPassword',
        componentProps: {
          autocomplete: 'current-password',
          placeholder: '请输入原密码',
        },
        fieldName: 'oldPassword',
        label: '原密码',
        rules: z
          .string({ error: '请输入原密码' })
          .min(1, { message: '请输入原密码' }),
      },
      {
        component: 'VbenInputPassword',
        componentProps: {
          autocomplete: 'new-password',
          passwordStrength: true,
          placeholder: '请输入新密码',
        },
        fieldName: 'newPassword',
        label: '新密码',
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
        component: 'VbenInputPassword',
        componentProps: {
          autocomplete: 'new-password',
          placeholder: '请再次输入新密码',
        },
        fieldName: 'confirmPassword',
        label: '确认新密码',
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
    ]),
    showDefaultActions: false,
  }),
);

/**
 * 提交修改密码：成功后重置状态并登出，要求使用新密码重新登录
 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) return;
  const values = await formApi.getValues();
  await changeOwnPassword(values.oldPassword, values.newPassword);
  authStore.forceChangePassword = false;
  // 改密后旧会话失效，登出并回到登录页
  await authStore.logout();
  message.success('密码修改成功，请使用新密码重新登录');
}

/**
 * 退出登录：放弃修改，直接登出
 */
async function handleLogout() {
  authStore.forceChangePassword = false;
  await authStore.logout();
}
</script>

<template>
  <Modal
    :closable="false"
    :keyboard="false"
    :mask-closable="false"
    :open="authStore.forceChangePassword"
    :width="440"
    title="密码安全要求：必须修改密码"
  >
    <p>您的登录密码已过期。根据安全合规要求，必须修改密码后才能继续使用系统。</p>
    <PasswordForm class="pt-2" />
    <template #footer>
      <div class="flex justify-end gap-2">
        <Button
          danger
          @click="handleLogout"
        >
          退出登录
        </Button>
        <Button
          type="primary"
          @click="handleSubmit"
        >
          修改密码
        </Button>
      </div>
    </template>
  </Modal>
</template>
