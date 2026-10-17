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

<script lang="ts" setup>
import type { DeviceAlertApi } from '#/api/device/alert';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  DatePicker,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tag,
} from 'ant-design-vue';

import { getDeviceList } from '#/api';
import {
  getDeviceAlertList,
  getDeviceAlertStats,
  updateDeviceAlert,
} from '#/api/device/alert';
import { $t } from '#/locales';

defineOptions({ name: 'DeviceAlert' });

const InputSearch = Input.Search;
const RangePicker = DatePicker.RangePicker;

const items = ref<DeviceAlertApi.DeviceAlert[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({
  deviceId: undefined as number | undefined,
  keyword: '',
  level: '' as string,
  page: 1,
  pageSize: 10,
  range: undefined as unknown as [string, string],
  status: '' as number | string,
});
const stats = ref<Record<string, number>>({});
const devices = ref<any[]>([]);

async function loadDevices() {
  const data = await getDeviceList({ page: 1, pageSize: 500 });
  devices.value = (data as any)?.items ?? [];
}

const LEVEL_META: Record<string, { color: string; label: string }> = {
  error: { color: 'error', label: $t('device.levelError') },
  info: { color: 'processing', label: $t('device.levelInfo') },
  warn: { color: 'warning', label: $t('device.levelWarn') },
};

const STATUS_META: Record<number, { color: string; label: string }> = {
  0: { color: 'error', label: '活跃' },
  1: { color: 'processing', label: '已确认' },
  2: { color: 'success', label: '已恢复' },
};

async function fetchList() {
  loading.value = true;
  try {
    const [startTime, endTime] = query.range && query.range.length === 2
      ? query.range : [undefined, undefined];
    const res = await getDeviceAlertList({
      deviceId: query.deviceId,
      endTime: endTime ? String(endTime) : undefined,
      keyword: query.keyword || undefined,
      level: query.level || undefined,
      page: query.page,
      pageSize: query.pageSize,
      startTime: startTime ? String(startTime) : undefined,
      status: query.status === '' ? undefined : Number(query.status),
    });
    items.value = ((res as any)?.items ?? []) as DeviceAlertApi.DeviceAlert[];
    total.value = Number((res as any)?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

async function loadStats() {
  stats.value = (await getDeviceAlertStats()) ?? {};
}

async function refresh() {
  await Promise.all([fetchList(), loadStats()]);
}

function onFilterChange() {
  query.page = 1;
  fetchList();
}

function onSearch(keyword: string) {
  query.keyword = keyword;
  onFilterChange();
}

function onAck(row: any) {
  updateDeviceAlert(row.id, { status: 1 }).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    refresh();
  });
}

const detailOpen = ref(false);
const detailRow = ref<null | Record<string, any>>(null);

function openDetail(row: Record<string, any>) {
  detailRow.value = row;
  detailOpen.value = true;
}

const columns = [
  { dataIndex: 'level', title: $t('device.level'), width: 90 },
  { dataIndex: 'deviceName', title: $t('device.name'), width: 160 },
  { dataIndex: 'content', title: $t('device.content') },
  { dataIndex: 'triggerCount', title: '次数', width: 70 },
  { dataIndex: 'lastTime', title: '最近触发', width: 160 },
  { dataIndex: 'status', title: '状态', width: 90 },
  { dataIndex: 'operation', title: $t('product.operation'), width: 130 },
];

onMounted(() => {
  refresh();
  loadDevices();
});
</script>
<template>
  <Page :title="$t('page.device.alert')">
    <div class="flex flex-col gap-4">
      <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <div
          v-for="tile in [
            { key: 'total', icon: 'icon-[lucide--bell]', label: $t('device.totalAlerts'), valueClass: 'text-primary' },
            { key: 'active', icon: 'icon-[lucide--bell-ring]', label: '活跃告警', valueClass: 'text-amber-500' },
            { key: 'acked', icon: 'icon-[lucide--circle-check]', label: '已确认', valueClass: 'text-sky-500' },
            { key: 'error', icon: 'icon-[lucide--octagon-alert]', label: $t('device.errorCount'), valueClass: 'text-red-500' },
          ]"
          :key="tile.key"
          class="bg-card hover:border-primary/40 flex items-center gap-4 rounded-lg border border-transparent p-5 shadow-sm transition-all hover:shadow-md"
        >
          <div class="bg-primary/10 flex size-12 shrink-0 items-center justify-center rounded-lg">
            <span :class="[tile.icon, tile.valueClass]" class="size-6"></span>
          </div>
          <div class="min-w-0">
            <div class="text-muted-foreground text-xs">{{ tile.label }}</div>
            <div class="text-foreground mt-1 text-2xl leading-none font-semibold tabular-nums">
              {{ stats[tile.key as keyof typeof stats] }}
            </div>
          </div>
        </div>
      </div>

      <div class="bg-card rounded-lg shadow-sm">
        <div class="flex flex-wrap items-center gap-3 p-4">
          <InputSearch
            v-model:value="query.keyword"
            :placeholder="`${$t('device.name') } / ${ $t('device.content')}`"
            class="max-w-64"
            allow-clear
            @search="onSearch"
          />
          <Select
            v-model:value="query.level"
            :options="[
              { label: $t('device.levelInfo'), value: 'info' },
              { label: $t('device.levelWarn'), value: 'warn' },
              { label: $t('device.levelError'), value: 'error' },
            ]"
            :placeholder="$t('device.level')"
            allow-clear
            class="w-32"
            @change="onFilterChange"
          />
          <Select
            v-model:value="query.status"
            :options="[
              { label: '活跃', value: 0 },
              { label: '已确认', value: 1 },
              { label: '已恢复', value: 2 },
            ]"
            placeholder="生命周期状态"
            allow-clear
            class="w-32"
            @change="onFilterChange"
          />
          <RangePicker v-model:value="query.range" value-format="YYYY-MM-DD" />
        </div>

        <div class="px-4 pb-4">
          <Table
            :columns="columns"
            :data-source="items"
            :loading="loading"
            :pagination="{
              current: query.page,
              pageSize: query.pageSize,
              total,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50'],
              showTotal: (t: number) => $t('product.thingModel.totalItems', [t]),
            }"
            row-key="id"
            @change="(p: any) => { query.page = p.current; query.pageSize = p.pageSize; fetchList(); }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'level'">
                <Tag :color="LEVEL_META[record.level]?.color" class="!m-0">
                  {{ LEVEL_META[record.level]?.label }}
                </Tag>
              </template>
              <template v-else-if="column.dataIndex === 'status'">
                <Tag :color="STATUS_META[record.status]?.color" class="!m-0">
                  {{ STATUS_META[record.status]?.label ?? record.status }}
                </Tag>
              </template>
              <template v-else-if="column.dataIndex === 'operation'">
                <Popconfirm
                  v-if="record.status === 0"
                  title="确认该告警？"
                  @confirm="onAck(record)"
                >
                  <Button type="link" size="small">确认</Button>
                </Popconfirm>
                <Button type="link" size="small" @click="openDetail(record)">详情</Button>
              </template>
            </template>
          </Table>
        </div>
      </div>
    </div>
    <Modal v-model:open="detailOpen" title="告警详情" destroy-on-close width="520px">
      <div v-if="detailRow" class="space-y-2 py-2 text-sm">
        <div class="flex gap-2"><span class="text-muted-foreground w-24 shrink-0">设备</span><span>{{ detailRow.deviceName }}</span></div>
        <div class="flex gap-2"><span class="text-muted-foreground w-24 shrink-0">内容</span><span>{{ detailRow.content }}</span></div>
        <div class="flex gap-2"><span class="text-muted-foreground w-24 shrink-0">最近值</span><span>{{ detailRow.lastValue ?? '-' }}</span></div>
        <div class="flex gap-2"><span class="text-muted-foreground w-24 shrink-0">累计触发</span><span>{{ detailRow.triggerCount ?? 1 }}</span></div>
        <div class="flex gap-2"><span class="text-muted-foreground w-24 shrink-0">最近触发</span><span>{{ detailRow.lastTime ?? '-' }}</span></div>
        <div class="flex gap-2"><span class="text-muted-foreground w-24 shrink-0">确认时间</span><span>{{ detailRow.ackTime ?? '-' }}</span></div>
        <div class="flex gap-2"><span class="text-muted-foreground w-24 shrink-0">恢复时间</span><span>{{ detailRow.clearTime ?? '-' }}</span></div>
        <div class="flex gap-2"><span class="text-muted-foreground w-24 shrink-0">恢复原因</span><span>{{ detailRow.clearReason ?? '-' }}</span></div>
      </div>
      <template #footer>
        <Button @click="detailOpen = false">关闭</Button>
      </template>
    </Modal>
  </Page>
</template>
