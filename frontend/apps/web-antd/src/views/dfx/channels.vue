<!--
MIT License

Copyright (c) 2026 materin-tech

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
-->

<script setup lang="ts">
import type { DfxApi } from '#/api/device/dfx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus, RotateCw } from '@vben/icons';

import {
  Button,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  createDfxChannel,
  deleteDfxChannel,
  getDfxChannelList,
  testDfxChannel,
  updateDfxChannel,
} from '#/api';
import { $t } from '#/locales';

defineOptions({ name: 'DfxChannels' });

const Textarea = Input.TextArea;

const TYPE_OPTIONS = [
  { label: 'Webhook', value: 'webhook' },
  { label: '邮件', value: 'email' },
  { label: '站内', value: 'inapp' },
];

const FORMAT_OPTIONS = [
  { label: '钉钉 DingTalk', value: 'dingtalk' },
  { label: '飞书 Feishu', value: 'feishu' },
  { label: '企业微信 WeCom', value: 'wecom' },
  { label: '通用 Generic', value: 'generic' },
];

const LEVEL_OPTIONS = [
  { label: 'info', value: 'info' },
  { label: 'warn', value: 'warn' },
  { label: 'error', value: 'error' },
];

const TYPE_META: Record<string, { color: string; label: string }> = {
  email: { color: 'purple', label: '邮件' },
  inapp: { color: 'blue', label: '站内' },
  webhook: { color: 'geekblue', label: 'Webhook' },
};

const LEVEL_COLOR: Record<string, string> = {
  error: 'red',
  info: 'blue',
  warn: 'orange',
};

const rows = ref<DfxApi.NotifyChannel[]>([]);
const loading = ref(false);

async function fetchList() {
  loading.value = true;
  try {
    const res = await getDfxChannelList();
    rows.value = ((res as any) ?? []) as DfxApi.NotifyChannel[];
  } finally {
    loading.value = false;
  }
}

function onToggle(row: any, checked: boolean) {
  updateDfxChannel(row.id, { enabled: checked ? 1 : 0 }).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    row.enabled = checked ? 1 : 0;
  });
}

async function onTest(row: Record<string, any>) {
  try {
    await testDfxChannel(row.id as number);
    message.success('测试消息已发送');
  } catch (err: any) {
    message.error(err?.message ?? '测试失败');
  }
}

// ---------- 新增/编辑 ----------
const formOpen = ref(false);
const formMode = ref<'create' | 'edit'>('create');
const form = reactive({
  config: {
    format: 'generic',
    subjectPrefix: '',
    to: '',
    url: '',
  },
  enabled: 1,
  id: undefined as number | undefined,
  minLevel: 'warn',
  name: '',
  remark: '',
  type: 'webhook' as DfxApi.NotifyChannelType,
});

function openCreate() {
  formMode.value = 'create';
  Object.assign(form, {
    config: {
      format: 'generic',
      subjectPrefix: '',
      to: '',
      url: '',
    },
    enabled: 1,
    id: undefined,
    minLevel: 'warn',
    name: '',
    remark: '',
    type: 'webhook',
  });
  formOpen.value = true;
}

function parseConfig(json?: string): any {
  if (!json) {
    return {};
  }
  try {
    return JSON.parse(json);
  } catch {
    return {};
  }
}

function onEdit(row: Record<string, any>) {
  formMode.value = 'edit';
  const config = parseConfig(row.configJson);
  Object.assign(form, {
    config: {
      format: config.format ?? 'generic',
      subjectPrefix: config.subjectPrefix ?? '',
      to: config.to ?? '',
      url: config.url ?? '',
    },
    enabled: row.enabled ?? 1,
    id: row.id,
    minLevel: row.minLevel ?? 'warn',
    name: row.name,
    remark: row.remark ?? '',
    type: row.type,
  });
  formOpen.value = true;
}

async function onSave() {
  if (!form.name) {
    message.error('请填写名称');
    return;
  }
  if (form.type === 'webhook' && !form.config.url) {
    message.error('请填写 Webhook 地址');
    return;
  }
  if (form.type === 'email' && !form.config.to) {
    message.error('请填写收件邮箱');
    return;
  }
  let configJson = '{}';
  if (form.type === 'webhook') {
    configJson = JSON.stringify({ format: form.config.format, url: form.config.url });
  } else if (form.type === 'email') {
    configJson = JSON.stringify({
      subjectPrefix: form.config.subjectPrefix,
      to: form.config.to,
    });
  }
  const payload: DfxApi.NotifyChannel = {
    configJson,
    enabled: form.enabled,
    minLevel: form.minLevel,
    name: form.name,
    remark: form.remark,
    type: form.type,
  };
  if (formMode.value === 'edit' && form.id) {
    await updateDfxChannel(form.id, payload);
  } else {
    await createDfxChannel(payload);
  }
  message.success($t('ui.actionMessage.operationSuccess'));
  formOpen.value = false;
  fetchList();
}

function onDelete(row: any) {
  deleteDfxChannel(row.id).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    fetchList();
  });
}

const columns = [
  { dataIndex: 'name', title: '名称' },
  { dataIndex: 'type', title: '类型', width: 110 },
  { dataIndex: 'minLevel', title: '最低级别', width: 100 },
  { dataIndex: 'enabled', title: '启用', width: 70 },
  { dataIndex: 'operation', title: $t('product.operation'), width: 190 },
];

onMounted(() => {
  fetchList();
});
</script>

<template>
  <Page :title="$t('page.dfx.channels.title')">
    <div class="bg-card rounded-lg shadow-sm">
      <div class="flex flex-wrap items-center gap-3 p-4">
        <Button @click="fetchList">
          <RotateCw class="size-5" />
          {{ $t('page.dfx.common.refresh') }}
        </Button>
        <div class="flex-1"></div>
        <Button type="primary" @click="openCreate">
          <Plus class="size-5" />
          新建渠道
        </Button>
      </div>

      <div class="px-4 pb-4">
        <Table
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="false"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'type'">
              <Tag
                :color="TYPE_META[record.type]?.color ?? 'default'"
                class="!m-0"
              >
                {{ TYPE_META[record.type]?.label ?? record.type }}
              </Tag>
            </template>
            <template v-else-if="column.dataIndex === 'minLevel'">
              <Tag :color="LEVEL_COLOR[record.minLevel] ?? 'default'" class="!m-0">
                {{ record.minLevel }}
              </Tag>
            </template>
            <template v-else-if="column.dataIndex === 'enabled'">
              <Switch
                :checked="record.enabled === 1"
                size="small"
                @change="(checked: any) => onToggle(record, checked)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'operation'">
              <Button type="link" size="small" @click="onTest(record)">测试</Button>
              <Button type="link" size="small" @click="onEdit(record)">编辑</Button>
              <Popconfirm title="确认删除该通知渠道？" @confirm="onDelete(record)">
                <Button type="link" size="small" danger>删除</Button>
              </Popconfirm>
            </template>
          </template>
        </Table>
      </div>
    </div>

    <Modal
      v-model:open="formOpen"
      :title="formMode === 'edit' ? '编辑通知渠道' : '新建通知渠道'"
      destroy-on-close
      width="560px"
    >
      <div class="space-y-3 py-2">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm">名称 *</div>
            <Input v-model:value="form.name" placeholder="如 运维钉钉群" />
          </div>
          <div>
            <div class="mb-1 text-sm">类型</div>
            <Select v-model:value="form.type" class="w-full" :options="TYPE_OPTIONS" />
          </div>
        </div>
        <div v-if="form.type === 'webhook'" class="space-y-3 rounded-md border p-3">
          <div>
            <div class="mb-1 text-sm">Webhook 地址 *</div>
            <Input v-model:value="form.config.url" placeholder="https://..." />
          </div>
          <div>
            <div class="mb-1 text-sm">消息格式</div>
            <Select v-model:value="form.config.format" class="w-full" :options="FORMAT_OPTIONS" />
          </div>
        </div>
        <div v-else-if="form.type === 'email'" class="space-y-3 rounded-md border p-3">
          <div>
            <div class="mb-1 text-sm">收件邮箱 *（多个用英文逗号分隔）</div>
            <Input v-model:value="form.config.to" placeholder="a@x.com, b@x.com" />
          </div>
          <div>
            <div class="mb-1 text-sm">邮件主题前缀（可选）</div>
            <Input v-model:value="form.config.subjectPrefix" placeholder="如 [格物告警]" />
          </div>
        </div>
        <div v-else class="rounded-md border p-3 text-sm text-gray-500">
          站内通知无需配置
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm">最低级别</div>
            <Select v-model:value="form.minLevel" class="w-full" :options="LEVEL_OPTIONS" />
          </div>
          <div>
            <div class="mb-1 text-sm">启用</div>
            <Switch v-model:checked="form.enabled" :checked-value="1" :un-checked-value="0" />
          </div>
        </div>
        <div>
          <div class="mb-1 text-sm">备注</div>
          <Textarea v-model:value="form.remark" :rows="2" />
        </div>
      </div>
      <template #footer>
        <Button @click="formOpen = false">{{ $t('common.cancel') }}</Button>
        <Button type="primary" @click="onSave">{{ $t('common.confirm') }}</Button>
      </template>
    </Modal>
  </Page>
</template>
