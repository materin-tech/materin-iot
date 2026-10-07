<script setup lang="ts">
import type { DfxApi } from '#/api/device/dfx';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, CheckboxGroup, Input, Modal, Select } from 'ant-design-vue';

import DfxChartCard from './chart-card.vue';
import {
  getDfxDashboards, getDfxHistory, getDfxMetricList, getDeviceList,
  getProductList, saveDfxDashboard,
} from '#/api';
import { $t } from '#/locales';

defineOptions({ name: 'DfxMetrics' });

const products = ref<any[]>([]);
const productId = ref<number>();
const devices = ref<any[]>([]);
const deviceKey = ref<string>();
const metricDict = ref<DfxApi.DfxMetricConfig[]>([]);
const metricMap = computed(() => {
  const m: Record<string, DfxApi.DfxMetricConfig> = {};
  for (const c of metricDict.value) {
    m[c.metric] = c;
  }
  return m;
});
const charts = ref<DfxApi.DfxChartConfig[]>([]);
const rows = ref<Record<string, any>[]>([]);
const editing = ref(false);
const loading = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;

const dictOptions = computed(() =>
  metricDict.value.map((c) => ({ label: `${c.name} (${c.metric})`, value: c.metric })));

// 新增图表表单
const addOpen = ref(false);
const form = ref<{ title: string; keys: string[]; yMax?: number; yMaxText?: string }>({
  title: '', keys: [], yMax: undefined,
});

function defaultCharts() {
  const keys = ['dfx_cpu_usage_pct', 'dfx_mem_usage_pct', 'dfx_disk_usage_pct', 'dfx_load_1m'];
  return [
    {
      title: 'CPU 使用率',
      metrics: keys.slice(0, 1).map((k) => ({
        key: k, name: metricMap.value[k]?.name ?? k,
      })),
      unit: '%',
      yMax: 100,
    },
    {
      title: '内存使用率',
      metrics: keys.slice(1, 2).map((k) => ({
        key: k, name: metricMap.value[k]?.name ?? k,
      })),
      unit: '%',
      yMax: 100,
    },
    {
      title: '磁盘/负载',
      metrics: keys.slice(2, 4).map((k) => ({
        key: k, name: metricMap.value[k]?.name ?? k,
      })),
      unit: '',
    },
  ];
}

async function loadDict() {
  metricDict.value = (await getDfxMetricList()) ?? [];
}

async function loadProducts() {
  const data = await getProductList({ page: 1, pageSize: 200 });
  products.value = (data as any)?.items ?? [];
  if (products.value.length > 0 && !productId.value) {
    productId.value = Number(products.value[0]?.id);
  }
}

async function loadDevices() {
  if (!productId.value) {
    devices.value = [];
    return;
  }
  const data = await getDeviceList({ page: 1, pageSize: 200, productId: productId.value });
  devices.value = (data as any)?.items ?? [];
  deviceKey.value = devices.value[0]?.deviceKey
      ? String(devices.value[0].deviceKey) : undefined;
}

async function loadDashboard() {
  if (!productId.value) {
    charts.value = [];
    return;
  }
  const list = (await getDfxDashboards(productId.value)) ?? [];
  const first = list[0];
  if (first?.configJson) {
    try {
      charts.value = (JSON.parse(first.configJson) as DfxApi.DfxDashboardConfig).charts ?? [];
      return;
    } catch {
      charts.value = [];
    }
  }
  charts.value = defaultCharts();
}

async function loadRows() {
  if (!deviceKey.value || !productId.value || charts.value.length === 0) {
    rows.value = [];
    return;
  }
  const device = devices.value.find((d) => String(d.deviceKey) === deviceKey.value);
  if (!device?.id) {
    return;
  }
  const keys = [...new Set(charts.value.flatMap((c) => c.metrics.map((m) => m.key)))];
  const end = Date.now();
  rows.value = (await getDfxHistory({
    deviceId: device.id, end, keys: keys.join(','), limit: 500,
    start: end - 3 * 3600 * 1000,
  })) as any;
}

async function refreshAll() {
  loading.value = true;
  try {
    await Promise.all([loadRows(), loadDict()]);
  } finally {
    loading.value = false;
  }
}

async function onProductChange() {
  await loadDevices();
  await loadDashboard();
  await loadRows();
}

function addChart() {
  if (!form.value.title || form.value.keys.length === 0) {
    return;
  }
  const yMax = form.value.yMaxText ? Number(form.value.yMaxText) : undefined;
  charts.value = [...charts.value, {
    title: form.value.title,
    metrics: form.value.keys.map((k) => ({
      key: k, name: metricMap.value[k]?.name ?? k,
    })),
    unit: metricMap.value[form.value.keys[0] ?? '']?.unit ?? '',
    yMax,
  }];
  form.value = { keys: [], title: '', yMax: undefined };
  addOpen.value = false;
}

function removeChart(idx: number) {
  charts.value = charts.value.filter((_, i) => i !== idx);
}

async function saveLayout() {
  await saveDfxDashboard({
    productId: productId.value!, name: '默认布局',
    configJson: JSON.stringify({ charts: charts.value }),
  });
  editing.value = false;
}

onMounted(async () => {
  await loadDict();
  await loadProducts();
  await onProductChange();
  timer = setInterval(refreshAll, 30_000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <Page :title="$t('page.dfx.metrics.title')">
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <Select v-model:value="productId" :options="products.map((p) => ({ label: p.name, value: Number(p.id) }))" class="w-56" placeholder="产品" @change="onProductChange" />
      <Select v-model:value="deviceKey" :options="devices.map((d) => ({ label: `${d.name} (${d.deviceKey})`, value: String(d.deviceKey) }))" class="w-64" placeholder="设备" show-search @change="loadRows" />
      <Button @click="refreshAll">{{ $t('page.dfx.common.refresh') }}</Button>
      <div class="ml-auto flex gap-2">
        <Button v-if="editing" @click="addOpen = true">添加图表</Button>
        <Button v-if="editing" type="primary" @click="saveLayout">保存布局</Button>
        <Button v-else @click="editing = true">编辑布局</Button>
      </div>
    </div>

    <div v-if="!deviceKey" class="rounded-lg border border-dashed p-8 text-center text-gray-400">
      该产品下暂无设备
    </div>
    <div v-else-if="charts.length === 0" class="rounded-lg border border-dashed p-8 text-center text-gray-400">
      暂无图表，点击"编辑布局"添加
    </div>
    <div v-else class="grid grid-cols-1 gap-4 xl:grid-cols-2">
      <div v-for="(c, i) in charts" :key="c.title + i" class="relative">
        <Button
          v-if="editing"
          class="absolute right-2 top-2 z-10"
          danger size="small"
          @click="removeChart(i)"
        >删除</Button>
        <DfxChartCard ref="cardsRef" :chart="c" :rows="rows" />
      </div>
    </div>

    <Modal v-model:open="addOpen" title="添加图表" @ok="addChart">
      <div class="space-y-3">
        <Input v-model:value="form.title" placeholder="图表标题（如：CPU 使用率）" />
        <CheckboxGroup v-model:value="form.keys" class="flex flex-col gap-1" :options="dictOptions" />
        <Input v-model:value="form.yMaxText" placeholder="Y 轴上限（可空，如 100）" />
      </div>
    </Modal>
  </Page>
</template>
