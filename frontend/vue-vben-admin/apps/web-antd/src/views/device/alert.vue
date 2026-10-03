<script lang="ts" setup>
import type { DeviceAlertApi } from '#/api/device/alert';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Input,
  message,
  Popconfirm,
  Select,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  getDeviceAlertList,
  updateDeviceAlert,
} from '#/api/device/alert';
import { $t } from '#/locales';

defineOptions({ name: 'DeviceAlert' });

const InputSearch = Input.Search;

const items = ref<DeviceAlertApi.DeviceAlert[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({ keyword: '', level: '', page: 1, pageSize: 10, status: '' });
const stats = ref({ error: 0, total: 0, unhandled: 0, warn: 0 });

const LEVEL_META: Record<string, { color: string; label: string }> = {
  error: { color: 'error', label: $t('device.levelError') },
  info: { color: 'processing', label: $t('device.levelInfo') },
  warn: { color: 'warning', label: $t('device.levelWarn') },
};

async function fetchList() {
  loading.value = true;
  try {
    const res = await getDeviceAlertList({
      keyword: query.keyword || undefined,
      level: query.level || undefined,
      status: query.status === '' ? undefined : query.status,
      page: query.page,
      pageSize: query.pageSize,
    });
    items.value = res.items;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

async function loadStats() {
  const [all, unhandled, warn, error] = await Promise.all([
    getDeviceAlertList({ page: 1, pageSize: 1 }),
    getDeviceAlertList({ page: 1, pageSize: 1, status: 0 }),
    getDeviceAlertList({ page: 1, pageSize: 1, level: 'warn' }),
    getDeviceAlertList({ page: 1, pageSize: 1, level: 'error' }),
  ]);
  stats.value = {
    error: error.total,
    total: all.total,
    unhandled: unhandled.total,
    warn: warn.total,
  };
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

function onMarkHandled(row: any) {
  updateDeviceAlert(row.id, { status: 1 }).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    refresh();
  });
}

const columns = [
  { dataIndex: 'level', title: $t('device.level'), width: 90 },
  { dataIndex: 'deviceName', title: $t('device.name'), width: 220 },
  { dataIndex: 'content', title: $t('device.content') },
  { dataIndex: 'status', title: $t('device.handleStatus'), width: 100 },
  { dataIndex: 'createTime', title: $t('product.createTime'), width: 170 },
  { dataIndex: 'operation', title: $t('product.operation'), width: 110 },
];

onMounted(() => {
  refresh();
});
</script>

<template>
  <Page :title="$t('page.device.alert')">
    <div class="flex flex-col gap-4">
      <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <div
          v-for="tile in [
            { key: 'total', icon: 'icon-[lucide--bell]', label: $t('device.totalAlerts'), valueClass: 'text-primary' },
            { key: 'unhandled', icon: 'icon-[lucide--bell-ring]', label: $t('device.unhandledCount'), valueClass: 'text-amber-500' },
            { key: 'warn', icon: 'icon-[lucide--triangle-alert]', label: $t('device.warnCount'), valueClass: 'text-orange-500' },
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
            :placeholder="$t('device.name') + ' / ' + $t('device.content')"
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
              { label: $t('device.unhandled'), value: '0' },
              { label: $t('device.handled'), value: '1' },
            ]"
            :placeholder="$t('device.handleStatus')"
            allow-clear
            class="w-32"
            @change="onFilterChange"
          />
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
                <Tag :color="record.status === 1 ? 'success' : 'warning'" class="!m-0">
                  {{ record.status === 1 ? $t('device.handled') : $t('device.unhandled') }}
                </Tag>
              </template>
              <template v-else-if="column.dataIndex === 'operation'">
                <Popconfirm
                  v-if="record.status === 0"
                  :title="$t('device.markHandled') + '?'"
                  @confirm="onMarkHandled(record)"
                >
                  <Button type="link" size="small">
                    {{ $t('device.markHandled') }}
                  </Button>
                </Popconfirm>
                <span v-else class="text-muted-foreground text-xs">—</span>
              </template>
            </template>
          </Table>
        </div>
      </div>
    </div>
  </Page>
</template>
