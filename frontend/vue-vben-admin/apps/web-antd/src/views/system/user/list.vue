<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type { Recordable } from '@vben/types';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemOrgApi, SystemUserApi } from '#/api';

import { onMounted, ref, watch } from 'vue';

import { Page, Tree, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, Card, Input, message, Modal } from 'ant-design-vue';

const InputSearch = Input.Search;

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import {
  deleteUser,
  getOrgList,
  getUserList,
  resetUserPassword,
  unlockUser,
  updateUser,
} from '#/api';
import { $t } from '#/locales';
import { createDateRangeCodec } from '#/utils/date-range-codec';

import { useColumns, useGridFormSchema } from './data';
import Detail from './modules/detail.vue';
import Form from './modules/form.vue';

interface UserSearchFormValues extends Record<string, unknown> {
  createTime?: [Dayjs, Dayjs];
}

const userSearchCodec = createDateRangeCodec<UserSearchFormValues>()({
  endField: 'endTime',
  rangeField: 'createTime',
  startField: 'startTime',
});

type UserSearchSubmitValues = ReturnType<typeof userSearchCodec.encode>;

const orgList = ref<SystemOrgApi.SystemOrg[]>([]);
const inputSearchValue = ref('');
const selectedOrgId = ref<string>('');

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [DetailDrawer, detailDrawerApi] = useVbenDrawer({
  connectedComponent: Detail,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    codec: userSearchCodec,
    schema: useGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns: useColumns(onStatusChange),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues: UserSearchSubmitValues) => {
          return await getUserList({
            page: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
            orgId: selectedOrgId.value,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },

    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      search: true,
      zoom: true,
    },
  } as VxeTableGridOptions<SystemUserApi.SystemUser>,
});

/**
 * 将Antd的Modal.confirm封装为promise，方便在异步函数中调用。
 * @param content 提示内容
 * @param title 提示标题
 */
function confirm(content: string, title: string) {
  return new Promise((reslove, reject) => {
    Modal.confirm({
      content,
      onCancel() {
        reject(new Error('已取消'));
      },
      onOk() {
        reslove(true);
      },
      title,
    });
  });
}

/**
 * 状态开关即将改变
 * @param newStatus 期望改变的状态值
 * @param row 行数据
 * @returns 返回false则中止改变，返回其他值（undefined、true）则允许改变
 */
async function onStatusChange(
  newStatus: number,
  row: SystemUserApi.SystemUser,
) {
  const status: Recordable<string> = {
    0: '禁用',
    1: '启用',
  };
  try {
    await confirm(
      `你要将${row.name}的状态切换为 【${status[newStatus.toString()]}】 吗？`,
      `切换状态`,
    );
    await updateUser(row.id, { status: newStatus });
    return true;
  } catch {
    return false;
  }
}

function onEdit(row: SystemUserApi.SystemUser) {
  formDrawerApi.setData(row).open();
}

function onDetail(row: SystemUserApi.SystemUser) {
  detailDrawerApi.setData(row).open();
}

function onDelete(row: SystemUserApi.SystemUser) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
    key: 'action_process_msg',
  });
  deleteUser(row.id)
    .then(() => {
      message.success({
        content: $t('ui.actionMessage.deleteSuccess', [row.name]),
        key: 'action_process_msg',
      });
      onRefresh();
    })
    .catch(() => {
      hideLoading();
    });
}

/** 生成密码的总长度（等保三级要求至少 8 位，这里取 12 位增强强度） */
const PASSWORD_LENGTH = 12;

/**
 * 基于安全随机源在 [0, max) 范围内取随机整数（拒绝采样，避免取模偏差）
 */
function randomInt(max: number): number {
  const range = 256 - (256 % max);
  const buffer = new Uint8Array(1);
  let value = 0;
  do {
    crypto.getRandomValues(buffer);
    value = buffer[0]!;
  } while (value >= range);
  return value % max;
}

/**
 * Fisher-Yates 洗牌，返回新数组（不改变原数组）
 */
function shuffle<T>(list: T[]): T[] {
  const result = [...list];
  for (let i = result.length - 1; i > 0; i--) {
    const j = randomInt(i + 1);
    [result[i], result[j]] = [result[j]!, result[i]!];
  }
  return result;
}

/** 等保三级：生成符合复杂度要求的随机密码（大写+小写+数字+特殊字符） */
function generateComplexPassword(): string {
  const sets = [
    'ABCDEFGHJKLMNPQRSTUVWXYZ',
    'abcdefghijkmnpqrstuvwxyz',
    '23456789',
    '!@#$%^&*',
  ];
  const all = sets.join('');
  // 每组字符集先各随机取 1 位，保证四类字符齐备
  const picks = sets.map((set) => set[randomInt(set.length)]!);
  // 剩余位数从全集中随机补齐
  while (picks.length < PASSWORD_LENGTH) {
    picks.push(all[randomInt(all.length)]!);
  }
  // 打乱顺序，避免四类字符固定出现在开头
  return shuffle(picks).join('');
}

/** 重置密码：生成新的符合复杂度的随机密码，确认后提交 */
function onResetPassword(row: SystemUserApi.SystemUser) {
  const newPassword = generateComplexPassword();
  Modal.confirm({
    content: `账号 ${row.name} 的新密码为：${newPassword}，请妥善保管。`,
    onOk: async () => {
      await resetUserPassword(row.id, newPassword);
      message.success($t('system.user.resetPassword') + '成功');
      onRefresh();
    },
    title: $t('system.user.resetPassword'),
  });
}

/** 解锁被锁定的账号 */
function onUnlock(row: SystemUserApi.SystemUser) {
  Modal.confirm({
    content: $t('system.user.unlockConfirm'),
    onOk: async () => {
      await unlockUser(row.id);
      message.success($t('system.user.unlock') + '成功');
    },
    title: $t('system.user.unlock'),
  });
}

function onRefresh() {
  gridApi.query();
}

function onCreate() {
  formDrawerApi.setData(null).open();
}

async function loadOrgList() {
  try {
    const res = await getOrgList();
    orgList.value = res;
  } catch (error) {
    console.error('Failed to load department list:', error);
  }
}

function selectOrg(v: string) {
  selectedOrgId.value = v;
  gridApi.query();
}

function searchOrg(value: string) {
  if (!value) {
    loadOrgList();
    return;
  }
  const filtered = orgList.value.filter((org) =>
    org.name.toLowerCase().includes(value.toLowerCase()),
  );
  orgList.value = filtered;
}

onMounted(() => {
  loadOrgList();
});

watch(inputSearchValue, (value) => {
  searchOrg(value);
});
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <DetailDrawer @success="onRefresh" />
    <div class="flex size-full">
      <Card class="w-1/6">
        <InputSearch
          v-model:value="inputSearchValue"
          :placeholder="$t('system.user.placeholder')"
        />
        <Tree
          label-field="name"
          value-field="id"
          :tree-data="orgList"
          :default-expanded-level="2"
          @select="selectOrg"
        />
      </Card>

      <div class="w-5/6 ml-4">
        <Grid :table-title="$t('system.user.list')">
          <template #toolbar-tools>
            <Button type="primary" @click="onCreate">
              <Plus class="size-5" />
              {{ $t('ui.actionTitle.create', [$t('system.user.name')]) }}
            </Button>
          </template>
          <template #action="{ row }">
            <VbenTableAction
              :actions="[
                {
                  text: $t('common.detail'),
                  icon: 'lucide:eye',
                  onClick: () => onDetail(row),
                },
                {
                  text: $t('common.edit'),
                  icon: 'lucide:edit',
                  onClick: () => onEdit(row),
                },
              ]"
              :dropdown-actions="[
                {
                  text: $t('system.user.resetPassword'),
                  icon: 'lucide:key-round',
                  onClick: () => onResetPassword(row),
                },
                {
                  text: $t('system.user.unlock'),
                  icon: 'lucide:unlock',
                  popConfirm: {
                    title: $t('system.user.unlockConfirm'),
                    confirm: () => onUnlock(row),
                  },
                },
                {
                  text: $t('common.delete'),
                  icon: 'lucide:trash-2',
                  danger: true,
                  popConfirm: {
                    title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                    confirm: () => onDelete(row),
                  },
                  auth: ['AC_100100'],
                },
              ]"
              align="center"
            />
          </template>
        </Grid>
      </div>
    </div>
  </Page>
</template>
