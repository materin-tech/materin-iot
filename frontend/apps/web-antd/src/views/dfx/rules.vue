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
import { Plus } from '@vben/icons';

import {
  Button,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  RadioGroup,
  Select,
  Switch,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  createDfxRule,
  deleteDfxRule,
  getDeviceList,
  getDfxMetricList,
  getDfxRuleList,
  getProductList,
  updateDfxRule,
} from '#/api';
import { $t } from '#/locales';

defineOptions({ name: 'DfxRules' });

const Textarea = Input.TextArea;

const COMPARATORS = [
  { label: '> 大于', value: 'gt' },
  { label: '>= 大于等于', value: 'gte' },
  { label: '< 小于', value: 'lt' },
  { label: '<= 小于等于', value: 'lte' },
  { label: '= 等于', value: 'eq' },
  { label: '!= 不等于', value: 'ne' },
  { label: 'between 区间', value: 'between' },
];

const LEVEL_OPTIONS = [
  { label: 'info 提示', value: 'info' },
  { label: 'warn 警告', value: 'warn' },
  { label: 'error 严重', value: 'error' },
];

const CLEAR_COMPARATORS = [
  { label: '< 恢复阈值', value: 'lt' },
  { label: '<= 恢复阈值', value: 'lte' },
  { label: '> 恢复阈值', value: 'gt' },
  { label: '>= 恢复阈值', value: 'gte' },
];

const rows = ref<DfxApi.DfxRule[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({
  keyword: '',
  level: undefined as string | undefined,
  page: 1,
  pageSize: 10,
  enabled: undefined as number | undefined,
});
const metricDict = ref<DfxApi.DfxMetricConfig[]>([]);
const products = ref<any[]>([]);
const devices = ref<any[]>([]);

async function fetchList() {
  loading.value = true;
  try {
    const res = await getDfxRuleList({
      keyword: query.keyword || undefined,
      level: query.level,
      page: query.page,
      pageSize: query.pageSize,
      enabled: query.enabled,
    });
    rows.value = ((res as any)?.items ?? []) as DfxApi.DfxRule[];
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
  updateDfxRule(row.id, { enabled: checked ? 1 : 0 }).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    row.enabled = checked ? 1 : 0;
  });
}

function metricLabel(metric: string) {
  const hit = metricDict.value.find((m) => m.metric === metric || `dfx_${metric}` === m.metric);
  return hit?.name ?? metric;
}

function comparatorText(row: Record<string, any>) {
  const t = row.threshold;
  const map: Record<string, string> = {
    gt: '>', gte: '>=', lt: '<', lte: '<=', eq: '=', ne: '!=',
  };
  if (row.comparator === 'between') {
    return `[${t}, ${row.threshold2 ?? '?'}]`;
  }
  return `${map[row.comparator] ?? row.comparator} ${t}`;
}

// ---------- 新增/编辑 ----------
const formOpen = ref(false);
const formMode = ref<'create' | 'edit'>('create');
const form = reactive({
  clearComparator: undefined as string | undefined,
  clearEnabled: false,
  clearThreshold: undefined as number | undefined,
  comparator: 'gt',
  deviceId: undefined as number | undefined,
  durationSeconds: 0,
  enabled: 1,
  id: undefined as number | undefined,
  level: 'warn',
  messageTemplate: '',
  metric: undefined as string | undefined,
  productId: undefined as number | undefined,
  remark: '',
  scope: 'device' as 'device' | 'product',
  suppressSeconds: 600,
  threshold: undefined as number | undefined,
  threshold2: undefined as number | undefined,
});

async function loadProducts() {
  const data = await getProductList({ page: 1, pageSize: 200 });
  products.value = (data as any)?.items ?? [];
}

async function loadDevices() {
  const data = await getDeviceList({ page: 1, pageSize: 500 });
  devices.value = (data as any)?.items ?? [];
}

function openCreate() {
  formMode.value = 'create';
  Object.assign(form, {
    clearComparator: undefined,
    clearEnabled: false,
    clearThreshold: undefined,
    comparator: 'gt',
    deviceId: undefined,
    durationSeconds: 0,
    enabled: 1,
    id: undefined,
    level: 'warn',
    messageTemplate: '',
    metric: undefined,
    productId: undefined,
    remark: '',
    scope: 'device',
    suppressSeconds: 600,
    threshold: undefined,
    threshold2: undefined,
  });
  formOpen.value = true;
}

function onEdit(row: Record<string, any>) {
  formMode.value = 'edit';
  const hasClear = !!row.clearComparator;
  Object.assign(form, {
    clearComparator: row.clearComparator,
    clearEnabled: hasClear,
    clearThreshold: row.clearThreshold,
    comparator: row.comparator,
    deviceId: row.deviceId,
    durationSeconds: row.durationSeconds ?? 0,
    enabled: row.enabled ?? 1,
    id: row.id,
    level: row.level ?? 'warn',
    messageTemplate: row.messageTemplate ?? '',
    metric: row.metric,
    productId: row.productId,
    remark: row.remark ?? '',
    scope: row.productId && !row.deviceId ? 'product' : 'device',
    suppressSeconds: row.suppressSeconds ?? 600,
    threshold: row.threshold,
    threshold2: row.threshold2,
  });
  formOpen.value = true;
}

async function onSave() {
  if (!form.metric) {
    message.error('请选择指标');
    return;
  }
  if (form.threshold === undefined || Number.isNaN(form.threshold)) {
    message.error('请填写触发阈值');
    return;
  }
  if (form.comparator === 'between'
      && (form.threshold2 === undefined || Number.isNaN(form.threshold2))) {
    message.error('between 条件需要填写区间上界');
    return;
  }
  if (form.scope === 'device' && !form.deviceId) {
    message.error('请选择设备');
    return;
  }
  if (form.scope === 'product' && !form.productId) {
    message.error('请选择产品');
    return;
  }
  if (form.clearEnabled && (!form.clearComparator || form.clearThreshold === undefined)) {
    message.error('恢复条件需要同时填写比较符与阈值');
    return;
  }
  const payload: DfxApi.DfxRule = {
    comparator: form.comparator,
    deviceId: form.scope === 'device' ? form.deviceId : undefined,
    durationSeconds: form.durationSeconds ?? 0,
    level: form.level,
    metric: form.metric,
    productId: form.scope === 'product' ? form.productId : undefined,
    remark: form.remark,
    suppressSeconds: form.suppressSeconds ?? 600,
    threshold: form.threshold,
    threshold2: form.comparator === 'between' ? form.threshold2 : undefined,
  };
  if (form.clearEnabled) {
    payload.clearComparator = form.clearComparator;
    payload.clearThreshold = form.clearThreshold;
  }
  if (formMode.value === 'edit' && form.id) {
    await updateDfxRule(form.id, payload);
  } else {
    await createDfxRule(payload);
  }
  message.success($t('ui.actionMessage.operationSuccess'));
  formOpen.value = false;
  fetchList();
}

function onDelete(row: any) {
  deleteDfxRule(row.id).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    fetchList();
  });
}

const columns = [
  { dataIndex: 'scope', title: '范围', width: 170 },
  { dataIndex: 'metric', title: '指标', width: 150 },
  { dataIndex: 'condition', title: '触发条件', width: 150 },
  { dataIndex: 'duration', title: '持续', width: 80 },
  { dataIndex: 'clearCondition', title: '恢复条件', width: 130 },
  { dataIndex: 'level', title: '级别', width: 80 },
  { dataIndex: 'enabled', title: '启用', width: 70 },
  { dataIndex: 'operation', title: $t('product.operation'), width: 130 },
];

onMounted(async () => {
  fetchList();
  metricDict.value = (await getDfxMetricList()) ?? [];
  loadProducts();
  loadDevices();
});
</script>

<template>
  <Page :title="$t('page.dfx.rules.title')">
    <div class="bg-card rounded-lg shadow-sm">
      <div class="flex flex-wrap items-center gap-3 p-4">
        <Input
          v-model:value="query.keyword"
          class="w-56"
          allow-clear
          placeholder="指标 / 设备名搜索"
          @press-enter="() => { query.page = 1; fetchList(); }"
        />
        <Select
          v-model:value="query.level"
          class="w-32"
          allow-clear
          :options="[{ label: 'info', value: 'info' }, { label: 'warn', value: 'warn' }, { label: 'error', value: 'error' }]"
          placeholder="级别"
          @change="() => { query.page = 1; fetchList(); }"
        />
        <Select
          v-model:value="query.enabled"
          class="w-28"
          allow-clear
          :options="[{ label: '已启用', value: 1 }, { label: '已停用', value: 0 }]"
          placeholder="状态"
          @change="() => { query.page = 1; fetchList(); }"
        />
        <div class="flex-1"></div>
        <Button type="primary" @click="openCreate">
          <Plus class="size-5" />
          新建规则
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
            <template v-if="column.dataIndex === 'scope'">
              <template v-if="record.deviceId">
                <Tag color="blue" class="!m-0">{{ record.deviceName || `设备#${record.deviceId}` }}</Tag>
              </template>
              <template v-else-if="record.productId">
                <Tag color="purple" class="!m-0">产品#{{ record.productId }}</Tag>
              </template>
              <template v-else>-</template>
            </template>
            <template v-else-if="column.dataIndex === 'metric'">
              {{ metricLabel(record.metric) }}
            </template>
            <template v-else-if="column.dataIndex === 'condition'">
              {{ comparatorText(record) }}
            </template>
            <template v-else-if="column.dataIndex === 'duration'">
              {{ record.durationSeconds ? `${record.durationSeconds}s` : '-' }}
            </template>
            <template v-else-if="column.dataIndex === 'clearCondition'">
              <template v-if="record.clearComparator">
                {{ record.clearComparator }} {{ record.clearThreshold }}
              </template>
              <template v-else>-</template>
            </template>
            <template v-else-if="column.dataIndex === 'level'">
              <Tag :color="record.level === 'error' ? 'red' : record.level === 'warn' ? 'orange' : 'blue'" class="!m-0">
                {{ record.level }}
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
              <Button type="link" size="small" @click="onEdit(record)">编辑</Button>
              <Popconfirm :title="`确认删除规则 #${record.id}？`" @confirm="onDelete(record)">
                <Button type="link" size="small" danger>删除</Button>
              </Popconfirm>
            </template>
          </template>
        </Table>
      </div>
    </div>

    <Modal
      v-model:open="formOpen"
      :title="formMode === 'edit' ? '编辑规则' : '新建规则'"
      destroy-on-close
      width="560px"
    >
      <div class="space-y-3 py-2">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm">规则范围 *</div>
            <RadioGroup
              v-model:value="form.scope"
              :options="[{ label: '单设备', value: 'device' }, { label: '按产品', value: 'product' }]"
            />
          </div>
          <div>
            <div class="mb-1 text-sm">级别</div>
            <Select v-model:value="form.level" class="w-full" :options="LEVEL_OPTIONS" />
          </div>
        </div>
        <div v-if="form.scope === 'device'">
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
        <div v-else>
          <div class="mb-1 text-sm">产品 *（该产品下所有设备自动生效）</div>
          <Select
            v-model:value="form.productId"
            class="w-full"
            show-search
            option-filter-prop="label"
            :options="products.map((p) => ({ label: p.name, value: Number(p.id) }))"
            placeholder="选择产品"
          />
        </div>
        <div class="grid grid-cols-3 gap-3">
          <div>
            <div class="mb-1 text-sm">指标 *</div>
            <Select
              v-model:value="form.metric"
              class="w-full"
              show-search
              option-filter-prop="label"
              :options="metricDict.map((m) => ({ label: `${m.name} (${m.metric})`, value: m.metric }))"
              placeholder="选择指标"
            />
          </div>
          <div>
            <div class="mb-1 text-sm">比较符 *</div>
            <Select v-model:value="form.comparator" class="w-full" :options="COMPARATORS" />
          </div>
          <div>
            <div class="mb-1 text-sm">
              {{ form.comparator === 'between' ? '下界 *' : '阈值 *' }}
            </div>
            <InputNumber v-model:value="form.threshold" class="!w-full" />
          </div>
        </div>
        <div v-if="form.comparator === 'between'">
          <div class="mb-1 text-sm">区间上界 *</div>
          <InputNumber v-model:value="form.threshold2" class="!w-full" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm">持续秒数（0=立即触发，防毛刺）</div>
            <InputNumber v-model:value="form.durationSeconds" :min="0" class="!w-full" />
          </div>
          <div>
            <div class="mb-1 text-sm">抑制窗口（秒）</div>
            <InputNumber v-model:value="form.suppressSeconds" :min="1" class="!w-full" />
          </div>
        </div>
        <div class="rounded-md border p-3">
          <div class="flex items-center gap-2">
            <Switch v-model:checked="form.clearEnabled" size="small" />
            <span class="text-sm">自动恢复条件（迟滞：指标回到正常区间即恢复告警）</span>
          </div>
          <div v-if="form.clearEnabled" class="mt-3 grid grid-cols-2 gap-3">
            <div>
              <div class="mb-1 text-sm">恢复比较符 *</div>
              <Select v-model:value="form.clearComparator" class="w-full" :options="CLEAR_COMPARATORS" />
            </div>
            <div>
              <div class="mb-1 text-sm">恢复阈值 *</div>
              <InputNumber v-model:value="form.clearThreshold" class="!w-full" />
            </div>
          </div>
        </div>
        <div>
          <div class="mb-1 text-sm">内容模板（占位符 {device} {metric} {value} {threshold}）</div>
          <Textarea v-model:value="form.messageTemplate" :rows="2" placeholder="留空使用默认格式" />
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
