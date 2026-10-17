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
import type { DeviceAlertApi } from '#/api/device/alert';
import type { DfxApi } from '#/api/device/dfx';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Card, Select, Table, Tag } from 'ant-design-vue';

import {
  getDeviceAlertList,
  getDeviceList,
  getDfxHistory,
  getDfxLatest,
  getDfxMetricList,
} from '#/api';
import { $t } from '#/locales';

import DfxChartCard from '../dfx/chart-card.vue';

defineOptions({ name: 'DeviceMonitor' });

/** 历史曲线回看窗口：最近 3 小时 */
const HISTORY_RANGE_MS = 3 * 3600 * 1000;
/** 历史查询点数上限 */
const HISTORY_LIMIT = 500;
/** 告警表取最近 10 条 */
const ALERT_PAGE_SIZE = 10;

const route = useRoute();

const devices = ref<any[]>([]);
const deviceKey = ref<string>();
const metricDict = ref<DfxApi.DfxMetricConfig[]>([]);
const latestRows = ref<
  {
    metric: string;
    name: string;
    time: number;
    unit: string;
    value: string;
  }[]
>([]);
const charts = ref<DfxApi.DfxChartConfig[]>([]);
const rows = ref<Record<string, any>[]>([]);
const alerts = ref<DeviceAlertApi.DeviceAlert[]>([]);
const alertsTotal = ref(0);
const loading = ref(false);

const deviceOptions = computed(() =>
  devices.value.map((d) => ({
    label: `${d.name} (${d.deviceKey})`,
    value: String(d.deviceKey),
  })),
);

const deviceId = computed(() => {
  const found = devices.value.find(
    (d) => String(d.deviceKey) === deviceKey.value,
  );
  return found ? Number(found.id) : undefined;
});

const metricMap = computed(() => {
  const m: Record<string, DfxApi.DfxMetricConfig> = {};
  for (const c of metricDict.value) {
    m[c.metric] = c;
  }
  return m;
});

const alertColumns = [
  { dataIndex: 'level', title: $t('device.level'), width: 90 },
  { dataIndex: 'content', title: $t('device.content') },
  { dataIndex: 'lastTime', title: '最近触发', width: 170 },
  { dataIndex: 'status', title: '状态', width: 90 },
];

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

function formatTime(ms: null | number | undefined) {
  if (!ms) {
    return '--';
  }
  return new Date(ms).toLocaleString();
}

async function loadDevices() {
  const data = await getDeviceList({ page: 1, pageSize: 200 });
  devices.value = (data as any)?.items ?? [];
}

/** 路由 ?device= 预选（兼容 deviceKey 与 id 两种取值） */
function applyRouteDevice(raw: unknown) {
  const q = raw === null || raw === undefined ? '' : String(raw);
  if (!q) {
    return;
  }
  const found = devices.value.find(
    (d) => String(d.deviceKey) === q || String(d.id) === q,
  );
  if (found) {
    deviceKey.value = String(found.deviceKey);
  }
}

/** 最新值与历史图表均由 latest 的 dfx_* 指标键推导 */
async function loadLatest() {
  if (!deviceKey.value) {
    latestRows.value = [];
    charts.value = [];
    return;
  }
  const latest = ((await getDfxLatest({ deviceKey: deviceKey.value })) ??
    {}) as Record<string, string>;
  const updated = latest.time ? Number(latest.time) : 0;
  const metrics = Object.keys(latest)
    .filter((k) => k !== 'source' && k !== 'time')
    .map((k) => ({
      metric: k,
      name: metricMap.value[k]?.name ?? k,
      time: updated,
      unit: metricMap.value[k]?.unit ?? '',
      value: String(latest[k] ?? '--'),
    }));
  latestRows.value = metrics;
  charts.value = metrics.map((m) => ({
    title: m.name,
    type: 'line',
    metrics: [{ key: m.metric, name: m.name }],
  }));
}

async function loadHistory() {
  if (!deviceId.value || charts.value.length === 0) {
    rows.value = [];
    return;
  }
  const keys = [
    ...new Set(charts.value.flatMap((c) => c.metrics.map((m) => m.key))),
  ];
  const end = Date.now();
  rows.value = ((await getDfxHistory({
    deviceId: deviceId.value,
    end,
    keys: keys.join(','),
    limit: HISTORY_LIMIT,
    start: end - HISTORY_RANGE_MS,
  })) ?? []) as Record<string, any>[];
}

async function loadAlerts() {
  if (!deviceId.value) {
    alerts.value = [];
    alertsTotal.value = 0;
    return;
  }
  const data = (await getDeviceAlertList({
    deviceId: deviceId.value,
    page: 1,
    pageSize: ALERT_PAGE_SIZE,
  })) as any;
  alerts.value = data?.items ?? [];
  alertsTotal.value = Number(data?.total ?? 0);
}

async function refreshAll() {
  loading.value = true;
  try {
    await loadLatest();
    await Promise.all([loadHistory(), loadAlerts()]);
  } finally {
    loading.value = false;
  }
}

function onDeviceChange() {
  refreshAll();
}

onMounted(async () => {
  await Promise.all([
    loadDevices(),
    getDfxMetricList().then((list) => {
      metricDict.value = list ?? [];
    }),
  ]);
  applyRouteDevice(route.query.device);
  await refreshAll();
});
</script>

<template>
  <Page :title="$t('page.device.monitor')">
    <div class="flex flex-col gap-4">
      <div class="flex flex-wrap items-center gap-3">
        <Select
          v-model:value="deviceKey"
          class="w-72"
          :options="deviceOptions"
          option-filter-prop="label"
          placeholder="选择设备"
          show-search
          @change="onDeviceChange"
        />
        <Button :loading="loading" @click="refreshAll">
          {{ $t('page.dfx.common.refresh') }}
        </Button>
      </div>

      <div v-if="!deviceKey" class="rounded-lg border border-dashed p-8 text-center text-gray-400">
        请先选择设备
      </div>
      <template v-else>
        <div v-if="latestRows.length === 0" class="rounded-lg border border-dashed p-8 text-center text-gray-400">
          暂无最新指标数据
        </div>
        <div v-else class="grid grid-cols-2 gap-3 md:grid-cols-4">
          <Card v-for="m in latestRows" :key="m.metric" :body-style="{ padding: '12px 16px' }" size="small">
            <div class="text-muted-foreground text-xs">{{ m.name }}</div>
            <div class="mt-1 flex items-baseline gap-1">
              <span class="text-2xl tabular-nums">{{ m.value }}</span>
              <span v-if="m.unit" class="text-muted-foreground text-xs">{{ m.unit }}</span>
            </div>
            <div class="text-muted-foreground mt-1 text-xs">
              {{ formatTime(m.time) }}
            </div>
          </Card>
        </div>

        <div v-if="charts.length > 0" class="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <DfxChartCard v-for="(c, i) in charts" :key="c.title + i" :chart="c" :rows="rows" />
        </div>

        <Card :body-style="{ padding: '0 16px 16px' }" size="small" title="最近告警">
          <Table
            :columns="alertColumns"
            :data-source="alerts"
            :loading="loading"
            :pagination="false"
            row-key="id"
            size="small"
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
            </template>
          </Table>
          <div v-if="alertsTotal === 0 && !loading" class="pt-2 text-center text-gray-400">
            暂无告警记录
          </div>
        </Card>
      </template>
    </div>
  </Page>
</template>
