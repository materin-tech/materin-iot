<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onBeforeUnmount, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Button, Card, Select } from 'ant-design-vue';

import { getDfxHistory, getDfxLatest, getDeviceList } from '#/api/device';
import { $t } from '#/locales';

defineOptions({ name: 'DeviceMonitor' });

interface DeviceRow {
  deviceId: number;
  label: string;
  value: string;
}

const deviceOptions = ref<DeviceRow[]>([]);
const currentDeviceKey = ref<string>();
const latest = ref<Record<string, string>>({});
const noData = ref(false);

const cpuRef = ref<EchartsUIType>();
const memRef = ref<EchartsUIType>();
const loadRef = ref<EchartsUIType>();
const { renderEcharts: renderCpu } = useEcharts(cpuRef);
const { renderEcharts: renderMem } = useEcharts(memRef);
const { renderEcharts: renderLoad } = useEcharts(loadRef);

let timer: ReturnType<typeof setInterval> | undefined;

function lineOption(
  title: string,
  series: { color: string; name: string; points: [number, number][] }[],
  yMax?: number,
) {
  return {
    grid: { bottom: 30, left: 44, right: 16, top: 36 },
    legend: { bottom: 0 },
    series: series.map((s) => ({
      data: s.points,
      itemStyle: { color: s.color },
      name: s.name,
      showSymbol: false,
      smooth: true,
      type: 'line' as const,
    })),
    title: { left: 'center', text: title, textStyle: { fontSize: 13 } },
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'time' as const },
    yAxis: { max: yMax, type: 'value' as const },
  };
}

function points(rows: Record<string, any>[], key: string): [number, number][] {
  return rows
    .filter((r) => r[key] !== null && r[key] !== undefined && r[key] !== '')
    .map((r) => [Number(r.time), Number(r[key])]);
}

async function loadLatest() {
  if (!currentDeviceKey.value) return;
  const data = await getDfxLatest({ deviceKey: currentDeviceKey.value });
  latest.value = data ?? {};
  noData.value = Object.keys(latest.value).length === 0;
}

async function loadHistory() {
  if (!currentDeviceKey.value) return;
  const device = deviceOptions.value.find((d) => d.value === currentDeviceKey.value);
  if (!device?.deviceId) return;
  const end = Date.now();
  const params = { deviceId: device.deviceId, end, limit: 500, start: end - 3 * 3600 * 1000 };
  const [cpu, mem, load] = await Promise.all([
    getDfxHistory({ ...params, keys: 'dfx_cpu_usage_pct' }),
    getDfxHistory({ ...params, keys: 'dfx_mem_usage_pct' }),
    getDfxHistory({ ...params, keys: 'dfx_load_1m,dfx_disk_usage_pct' }),
  ]);
  const rowsCpu = cpu as unknown as Record<string, any>[];
  const rowsMem = mem as unknown as Record<string, any>[];
  const rowsLoad = load as unknown as Record<string, any>[];
  renderCpu(lineOption($t('page.device.monitor.cpu'), [
    { color: '#1677ff', name: 'CPU', points: points(rowsCpu, 'dfx_cpu_usage_pct') },
  ], 100));
  renderMem(lineOption($t('page.device.monitor.mem'), [
    { color: '#10b981', name: 'MEM', points: points(rowsMem, 'dfx_mem_usage_pct') },
  ], 100));
  renderLoad(lineOption($t('page.device.monitor.load'), [
    { color: '#f59e0b', name: 'Load', points: points(rowsLoad, 'dfx_load_1m') },
    { color: '#a855f7', name: 'Disk', points: points(rowsLoad, 'dfx_disk_usage_pct') },
  ]));
}

async function refreshAll() {
  await Promise.all([loadLatest(), loadHistory()]);
}

function onDeviceChange(key: unknown) {
  currentDeviceKey.value = String(key);
  refreshAll();
}

onMounted(async () => {
  const data = await getDeviceList({ page: 1, pageSize: 200 });
  const items = (data as any)?.items ?? [];
  deviceOptions.value = items.map((d: any) => ({
    deviceId: Number(d.id),
    label: `${d.name} (${d.deviceKey})`,
    value: String(d.deviceKey),
  }));
  if (deviceOptions.value.length > 0) {
    currentDeviceKey.value = deviceOptions.value[0]?.value;
    await refreshAll();
  }
  timer = setInterval(refreshAll, 30_000);
});

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <Page :title="$t('page.device.monitor.title')">
    <div class="mb-4 flex items-center gap-3">
      <span class="text-sm">{{ $t('page.device.monitor.pickDevice') }}</span>
      <Select
        v-model:value="currentDeviceKey"
        :options="deviceOptions"
        class="w-72"
        show-search
        @change="onDeviceChange"
      />
      <Button @click="refreshAll">{{ $t('page.device.monitor.refresh') }}</Button>
    </div>

    <div v-if="noData" class="rounded-lg border border-dashed p-8 text-center text-gray-400">
      {{ $t('page.device.monitor.noData') }}
    </div>

    <div class="grid grid-cols-1 gap-4">
      <Card><EchartsUI ref="cpuRef" height="240px" /></Card>
      <Card><EchartsUI ref="memRef" height="240px" /></Card>
      <Card><EchartsUI ref="loadRef" height="240px" /></Card>
    </div>

    <Card class="mt-4" :title="$t('page.device.monitor.latest')">
      <div class="grid grid-cols-2 gap-2 text-sm md:grid-cols-4">
        <div
          v-for="(v, k) in latest"
          :key="k"
          class="bg-accent rounded-md px-3 py-2"
        >
          <span class="text-muted-foreground">{{ k }}</span>
          <span class="ml-2 tabular-nums">{{ v }}</span>
        </div>
      </div>
    </Card>
  </Page>
</template>
