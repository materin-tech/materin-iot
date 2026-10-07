<script lang="ts" setup>
import type { DataNode } from 'ant-design-vue/es/tree';

import type { Recordable } from '@vben/types';

import type { SystemUserApi } from '#/api/system/user';

import { computed, h, nextTick, reactive, ref } from 'vue';


import { Tree, useVbenDrawer, VbenInputPassword } from '@vben/common-ui';

import { IconifyIcon } from '@vben/icons';

import { Button, Modal, Spin, Typography } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getMenuList } from '#/api/system/menu';
import { createUser, updateUser } from '#/api/system/user';
import { $t } from '#/locales';
import { generateComplexPassword } from '#/utils/password-generator';

import { useFormSchema } from '../data';

const emits = defineEmits(['success']);

const formData = ref<SystemUserApi.SystemUser>();

const isEdit = computed(() => !!formData.value?.id);

const [Form, formApi] = useVbenForm(
  reactive({
    // 创建时才显示密码字段（编辑时后端把空密码视为不修改）
    schema: computed(() => useFormSchema(isEdit.value)),
    showDefaultActions: false,
  }),
);

const permissions = ref<DataNode[]>([]);
const loadingPermissions = ref(false);

const id = ref();
const [Drawer, drawerApi] = useVbenDrawer<null | SystemUserApi.SystemUser>({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    drawerApi.lock();
    (id.value ? updateUser(id.value, values) : createUser(values))
      .then(() => {
        // 创建分支：弹出账号信息交付面板（编辑分支不弹）
        if (!id.value) {
          showAccountDeliveryPanel(values.name as string, values.password as string);
        }
        emits('success');
        drawerApi.close();
      })
      .catch(() => {
        drawerApi.unlock();
      });
  },

  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = drawerApi.getData();
      formApi.reset();

      if (data) {
        formData.value = data;
        id.value = data.id;
      } else {
        formData.value = undefined;
        id.value = undefined;
      }

      if (permissions.value.length === 0) {
        await loadPermissions();
      }
      // Wait for Vue to flush DOM updates (form fields mounted)
      await nextTick();
      if (data) {
        formApi.setValues(data);
      }
    }
  },
});

defineExpose({ drawerApi });

/** 生成随机密码并回填到密码输入框（保持复杂度校验） */
function handleGeneratePassword() {
  formApi.setFieldValue('password', generateComplexPassword());
}

/**
 * 账号信息交付面板：创建用户成功后弹窗展示账号与初始密码，
 * 提示管理员立即复制保存（密码仅此一次明文展示）
 */
function showAccountDeliveryPanel(name: string, password: string) {
  Modal.success({
    title: '请妥善保存账号信息',
    content: h('div', {}, [
      h('p', {}, [
        '账号：',
        h(
          Typography.Text,
          { style: { userSelect: 'all', fontWeight: 'bold' } },
          { default: () => name },
        ),
      ]),
      h('p', {}, [
        '登录密码：',
        h(
          Typography.Text,
          { style: { userSelect: 'all', fontWeight: 'bold' } },
          { default: () => password },
        ),
      ]),
      h(
        'p',
        { style: { color: '#faad14', marginTop: '8px' } },
        '登录密码仅此一次明文展示，请立即复制保存',
      ),
    ]),
    okText: '我已保存',
    width: 420,
  });
}

async function loadPermissions() {
  loadingPermissions.value = true;
  try {
    const res = await getMenuList();
    permissions.value = res as unknown as DataNode[];
  } finally {
    loadingPermissions.value = false;
  }
}

const getDrawerTitle = computed(() => {
  return formData.value?.id
    ? $t('common.edit', $t('system.user.name'))
    : $t('common.create', $t('system.user.name'));
});

function getNodeClass(node: Recordable<any>) {
  const classes: string[] = [];
  if (node.value?.type === 'button') {
    classes.push('inline-flex');
  }

  return classes.join(' ');
}
</script>
<template>
  <Drawer :title="getDrawerTitle">
    <Form>
      <template #password="slotProps">
        <div class="flex w-full items-center gap-2">
          <VbenInputPassword
            v-bind="slotProps.componentProps"
            class="flex-1"
          />
          <Button
            class="shrink-0"
            @click="handleGeneratePassword"
          >
            <template #icon>
              <IconifyIcon icon="lucide:dices" />
            </template>
            生成随机密码
          </Button>
        </div>
      </template>
      <template #permissions="slotProps">
        <Spin
          :spinning="loadingPermissions"
          wrapper-class-name="w-full"
        >
          <Tree
            :tree-data="permissions"
            multiple
            bordered
            :default-expanded-level="2"
            :get-node-class="getNodeClass"
            v-bind="slotProps.componentProps"
            value-field="id"
            label-field="name"
          />
        </Spin>
      </template>
    </Form>
  </Drawer>
</template>
