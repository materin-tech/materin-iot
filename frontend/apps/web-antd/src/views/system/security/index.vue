<script lang="ts" setup>
import type { SystemConfigApi } from '#/api/system/config';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getSecurityConfig, updateSecurityConfig } from '#/api/system/config';
import { $t } from '#/locales';

/** 是否正在加载配置（加载中禁用提交按钮） */
const loading = ref(false);

/**
 * 安全设置表单：
 * - 密码策略：最小长度、四类字符开关、有效期/提醒天数
 * - 登录锁定策略：最大失败次数、锁定时长
 */
const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'horizontal',
  schema: [
    {
      component: 'InputNumber',
      componentProps: {
        max: 32,
        min: 1,
        precision: 0,
      },
      fieldName: 'passwordMinLength',
      label: $t('system.security.passwordMinLength'),
    },
    {
      component: 'Switch',
      fieldName: 'passwordRequireUppercase',
      label: $t('system.security.requireUppercase'),
    },
    {
      component: 'Switch',
      fieldName: 'passwordRequireLowercase',
      label: $t('system.security.requireLowercase'),
    },
    {
      component: 'Switch',
      fieldName: 'passwordRequireDigit',
      label: $t('system.security.requireDigit'),
    },
    {
      component: 'Switch',
      fieldName: 'passwordRequireSpecial',
      label: $t('system.security.requireSpecial'),
    },
    {
      component: 'InputNumber',
      componentProps: {
        max: 365,
        min: 1,
        precision: 0,
      },
      fieldName: 'passwordExpireDays',
      label: $t('system.security.passwordExpireDays'),
    },
    {
      component: 'InputNumber',
      componentProps: {
        max: 90,
        min: 1,
        precision: 0,
      },
      fieldName: 'passwordRemindDays',
      label: $t('system.security.passwordRemindDays'),
    },
    {
      component: 'InputNumber',
      componentProps: {
        max: 20,
        min: 1,
        precision: 0,
      },
      fieldName: 'lockoutMaxFails',
      label: $t('system.security.lockoutMaxFails'),
    },
    {
      component: 'InputNumber',
      componentProps: {
        max: 1440,
        min: 1,
        precision: 0,
      },
      fieldName: 'lockoutDurationMinutes',
      label: $t('system.security.lockoutDurationMinutes'),
    },
  ],
  showDefaultActions: false,
});

/** 加载当前安全配置并填充表单 */
async function loadConfig() {
  loading.value = true;
  try {
    const config: SystemConfigApi.SecurityConfig = await getSecurityConfig();
    formApi.setValues(config, false);
  } catch (error) {
    console.error('Failed to load security config:', error);
  } finally {
    loading.value = false;
  }
}

/** 提交安全配置 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }
  const values = await formApi.getValues();
  await updateSecurityConfig(values as Partial<SystemConfigApi.SecurityConfig>);
  message.success($t('system.security.saved'));
}

onMounted(() => {
  loadConfig();
});
</script>
<template>
  <Page :title="$t('system.security.title')">
    <Card :loading="loading" :title="$t('system.security.title')">
      <Form class="max-w-md" />
      <Button
        :disabled="loading"
        class="mt-4"
        type="primary"
        @click="handleSubmit"
      >
        {{ $t('system.security.save') }}
      </Button>
    </Card>
  </Page>
</template>
