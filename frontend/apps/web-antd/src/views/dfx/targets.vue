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
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  createDfxTarget,
  deleteDfxTarget,
  getDeviceList,
  getDfxTargetList,
  updateDfxTarget,
} from '#/api';
import { $t } from '#/locales';

defineOptions({ name: 'DfxTargets' });

const Textarea = Input.TextArea;

const VERSION_OPTIONS = [
  { label: 'v2c', value: 'v2c' },
  { label: 'v3', value: 'v3' },
];

const AUTH_PROTOCOLS = [
  { label: 'SHA', value: 'SHA' },
  { label: 'MD5', value: 'MD5' },
];

const PRIV_PROTOCOLS = [
  { label: 'AES128', value: 'AES128' },
  { label: 'DES', value: 'DES' },
];

const rows = ref<DfxApi.DfxTarget[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({
  page: 1,
  pageSize: 10,
});
const devices = ref<any[]>([]);

async function fetchList() {
  loading.value = true;
  try {
    const res = await getDfxTargetList({
      page: query.page,
      pageSize: query.pageSize,
    });
    rows.value = ((res as any)?.items ?? []) as DfxApi.DfxTarget[];
    total.value = Number((res as any)?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

function onTableChange(p: any) {
  query.page = p.current;
  query.pageSize = p.pageSize;
  fetchList();
}

function onToggle(row: any, checked: boolean) {
  updateDfxTarget(row.id, { enabled: checked ? 1 : 0 }).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    row.enabled = checked ? 1 : 0;
  });
}

async function loadDevices() {
  const data = await getDeviceList({ page: 1, pageSize: 500 });
  devices.value = (data as any)?.items ?? [];
}

// ---------- 新增/编辑 ----------
const formOpen = ref(false);
const formMode = ref<'create' | 'edit'>('create');
const form = reactive({
  community: '',
  deviceId: undefined as number | undefined,
  host: '',
  id: undefined as number | undefined,
  intervalSeconds: 300,
  port: 161,
  remark: '',
  v3AuthPassphrase: '',
  v3AuthProtocol: 'SHA',
  v3PrivPassphrase: '',
  v3PrivProtocol: 'AES128',
  v3User: '',
  version: 'v2c',
});

function openCreate() {
  formMode.value = 'create';
  Object.assign(form, {
    community: '',
    deviceId: undefined,
    host: '',
    id: undefined,
    intervalSeconds: 300,
    port: 161,
    remark: '',
    v3AuthPassphrase: '',
    v3AuthProtocol: 'SHA',
    v3PrivPassphrase: '',
    v3PrivProtocol: 'AES128',
    v3User: '',
    version: 'v2c',
  });
  formOpen.value = true;
}

function onEdit(row: Record<string, any>) {
  formMode.value = 'edit';
  Object.assign(form, {
    community: row.community ?? '',
    deviceId: row.deviceId,
    host: row.host,
    id: row.id,
    intervalSeconds: row.intervalSeconds ?? 300,
    port: row.port ?? 161,
    remark: row.remark ?? '',
    v3AuthPassphrase: row.v3AuthPassphrase ?? '',
    v3AuthProtocol: row.v3AuthProtocol ?? 'SHA',
    v3PrivPassphrase: row.v3PrivPassphrase ?? '',
    v3PrivProtocol: row.v3PrivProtocol ?? 'AES128',
    v3User: row.v3User ?? '',
    version: row.version ?? 'v2c',
  });
  formOpen.value = true;
}

async function onSave() {
  if (!form.deviceId) {
    message.error('请选择设备');
    return;
  }
  if (!form.host) {
    message.error('请填写主机地址');
    return;
  }
  if (form.version === 'v2c' && !form.community) {
    message.error('v2c 需要填写团体字');
    return;
  }
  if (form.version === 'v3' && !form.v3User) {
    message.error('v3 需要填写用户名');
    return;
  }
  const payload: DfxApi.DfxTarget = {
    deviceId: form.deviceId,
    host: form.host,
    port: form.port ?? 161,
    version: form.version,
    intervalSeconds: form.intervalSeconds ?? 300,
    remark: form.remark,
  };
  if (form.version === 'v3') {
    payload.v3User = form.v3User;
    payload.v3AuthProtocol = form.v3AuthProtocol;
    payload.v3AuthPassphrase = form.v3AuthPassphrase;
    payload.v3PrivProtocol = form.v3PrivProtocol;
    payload.v3PrivPassphrase = form.v3PrivPassphrase;
  } else {
    payload.community = form.community;
  }
  if (formMode.value === 'edit' && form.id) {
    await updateDfxTarget(form.id, payload);
  } else {
    await createDfxTarget(payload);
  }
  message.success($t('ui.actionMessage.operationSuccess'));
  formOpen.value = false;
  fetchList();
}

function onDelete(row: any) {
  deleteDfxTarget(row.id).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    fetchList();
  });
}

const columns = [
  { dataIndex: 'deviceName', title: '设备', width: 160 },
  { dataIndex: 'target', title: '目标', width: 190 },
  { dataIndex: 'version', title: '协议版本', width: 90 },
  { dataIndex: 'intervalSeconds', title: '间隔', width: 80 },
  { dataIndex: 'enabled', title: '启用', width: 70 },
  { dataIndex: 'operation', title: $t('product.operation'), width: 130 },
];

onMounted(() => {
  fetchList();
  loadDevices();
});
</script>

<template>
  <Page :title="$t('page.dfx.targets.title')">
    <div class="bg-card rounded-lg shadow-sm">
      <div class="flex flex-wrap items-center gap-3 p-4">
        <Button @click="fetchList">
          <RotateCw class="size-5" />
          {{ $t('page.dfx.common.refresh') }}
        </Button>
        <div class="flex-1"></div>
        <Button type="primary" @click="openCreate">
          <Plus class="size-5" />
          新建目标
        </Button>
      </div>

      <div class="px-4 pb-4">
        <Table
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="{
            current: query.page,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          row-key="id"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'deviceName'">
              {{ record.deviceName || `设备#${record.deviceId}` }}
            </template>
            <template v-else-if="column.dataIndex === 'target'">
              {{ record.host }}:{{ record.port ?? 161 }}
            </template>
            <template v-else-if="column.dataIndex === 'version'">
              <Tag :color="record.version === 'v3' ? 'geekblue' : 'default'" class="!m-0">
                {{ record.version || 'v2c' }}
              </Tag>
            </template>
            <template v-else-if="column.dataIndex === 'intervalSeconds'">
              {{ `${record.intervalSeconds}s` }}
            </template>
            <template v-else-if="column.dataIndex === 'enabled'">
              <Switch
                :checked="record.enabled === 1"
                size="small"
                @change="(checked: any) => onToggle(record, checked)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'operation'">
              <Button type="link" size="small" @click="onEdit(record)">编辑</Button>
              <Popconfirm title="确认删除该采集目标？" @confirm="onDelete(record)">
                <Button type="link" size="small" danger>删除</Button>
              </Popconfirm>
            </template>
          </template>
        </Table>
      </div>
    </div>

    <Modal
      v-model:open="formOpen"
      :title="formMode === 'edit' ? '编辑采集目标' : '新建采集目标'"
      destroy-on-close
      width="560px"
    >
      <div class="space-y-3 py-2">
        <div>
          <div class="mb-1 text-sm">设备 *</div>
          <Select
            v-model:value="form.deviceId"
            class="w-full"
            show-search
            option-filter-prop="label"
            :options="devices.map((d) => ({ label: d.name, value: Number(d.id) }))"
            placeholder="选择设备"
          />
        </div>
        <div class="grid grid-cols-3 gap-3">
          <div class="col-span-2">
            <div class="mb-1 text-sm">主机地址 *</div>
            <Input v-model:value="form.host" placeholder="如 192.168.1.10" />
          </div>
          <div>
            <div class="mb-1 text-sm">端口</div>
            <InputNumber v-model:value="form.port" :min="1" :max="65535" class="!w-full" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm">协议版本</div>
            <Select v-model:value="form.version" class="w-full" :options="VERSION_OPTIONS" />
          </div>
          <div>
            <div class="mb-1 text-sm">间隔（秒）</div>
            <InputNumber v-model:value="form.intervalSeconds" :min="5" class="!w-full" />
          </div>
        </div>
        <div v-if="form.version === 'v2c'">
          <div class="mb-1 text-sm">团体字（community）*</div>
          <Input v-model:value="form.community" placeholder="如 public" />
        </div>
        <div v-if="form.version === 'v3'" class="space-y-3 rounded-md border p-3">
          <div class="text-sm font-medium">SNMPv3 认证</div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <div class="mb-1 text-sm">用户名 *</div>
              <Input v-model:value="form.v3User" />
            </div>
            <div>
              <div class="mb-1 text-sm">认证协议</div>
              <Select v-model:value="form.v3AuthProtocol" class="w-full" :options="AUTH_PROTOCOLS" />
            </div>
          </div>
          <div>
            <div class="mb-1 text-sm">认证密码</div>
            <Input.Password v-model:value="form.v3AuthPassphrase" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <div class="mb-1 text-sm">加密协议</div>
              <Select v-model:value="form.v3PrivProtocol" class="w-full" :options="PRIV_PROTOCOLS" />
            </div>
            <div>
              <div class="mb-1 text-sm">加密密码</div>
              <Input.Password v-model:value="form.v3PrivPassphrase" />
            </div>
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
