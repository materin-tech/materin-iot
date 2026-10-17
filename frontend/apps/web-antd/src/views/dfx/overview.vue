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
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { DfxApi } from '#/api/device/dfx';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Button, Card } from 'ant-design-vue';

import { getDfxOverview } from '#/api';
import { $t } from '#/locales';

defineOptions({ name: 'DfxOverview' });

const STATUS_COLOR: Record<string, string> = {
  DOWN: '#ef4444',
  UP: '#10b981',
};

const overview = ref<DfxApi.DfxOverview | null>(null);
const loading = ref(false);
const trendDays = ref(7);

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

async function load() {
  loading.value = true;
  try {
    overview.value = (await getDfxOverview(trendDays.value)) as any;
    applyTiles();
    renderTrend();
  } finally {
    loading.value = false;
  }
}

function renderTrend() {
  const trend = overview.value?.trend ?? [];
  renderEcharts({
    grid: { bottom: 30, left: 40, right: 16, top: 36 },
    series: [
      {
        barMaxWidth: 32,
        data: trend.map((t) => [t.date, t.total]),
        itemStyle: { color: '#1677ff' },
        type: 'bar',
      },
    ],
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'time' },
    yAxis: { minInterval: 1, type: 'value' },
  });
}

const tiles = ref([
  { icon: 'icon-[lucide--server]', key: 'devices', label: '设备总数', value: 0 },
  { icon: 'icon-[lucide--wifi]', key: 'online', label: '在线', value: 0 },
  { icon: 'icon-[lucide--unplug]', key: 'offline', label: '离线', value: 0 },
  { icon: 'icon-[lucide--bell-ring]', key: 'alerts', label: '活跃告警', value: 0 },
]);

function applyTiles() {
  const d = overview.value?.devices;
  const alerts = overview.value?.alerts ?? {};
  const values: Record<string, number> = {
    devices: d?.total ?? 0,
    online: d?.online ?? 0,
    offline: d?.offline ?? 0,
    alerts: Number(alerts.active ?? 0),
  };
  tiles.value = tiles.value.map((tile) => ({ ...tile, value: values[tile.key] ?? 0 }));
}

onMounted(() => {
  load();
});
</script>
<template>
  <Page :title="$t('page.dfx.overview.title')">
    <div class="mb-4 flex items-center gap-3">
      <Button :loading="loading" @click="load">{{ $t('page.dfx.common.refresh') }}</Button>
    </div>

      <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <div
          v-for="tile in tiles"
          :key="tile.key"
          class="bg-card hover:border-primary/40 flex items-center gap-4 rounded-lg border border-transparent p-5 shadow-sm transition-all hover:shadow-md"
        >
          <div class="bg-primary/10 flex size-12 shrink-0 items-center justify-center rounded-lg">
            <span :class="[tile.icon]" class="size-6"></span>
          </div>
          <div class="min-w-0">
            <div class="text-muted-foreground text-xs">{{ tile.label }}</div>
            <div class="text-foreground mt-1 text-2xl leading-none font-semibold tabular-nums">
              {{ tile.value }}
            </div>
          </div>
        </div>
      </div>
    <Card title="告警趋势">
      <EchartsUI ref="chartRef" />
    </Card>
    <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
      <Card v-for="c in overview?.components ?? []" :key="c.name" class="overflow-hidden">
        <div class="flex items-center gap-2">
          <span
            class="size-3 rounded-full"
            :style="{ backgroundColor: STATUS_COLOR[c.status] ?? '#9ca3af' }"
          ></span>
          <span class="text-base font-medium uppercase">{{ c.name }}</span>
          <span
            class="ml-auto text-sm"
            :style="{ color: STATUS_COLOR[c.status] ?? '#9ca3af' }"
          >
            {{ c.status }}
          </span>
        </div>
        <div class="text-muted-foreground mt-3 text-sm leading-6">
          {{ c.detail }}
        </div>
        <div class="text-muted-foreground mt-1 text-xs">
          最近接入：{{ c.lastIngestAt ? new Date(c.lastIngestAt).toLocaleString() : '—' }}
        </div>
      </Card>
    </div>
    <Card class="mt-4" :title="$t('page.dfx.components.pipeline')">
      <div
        v-if="overview?.pipeline && Object.keys(overview.pipeline).length"
        class="grid grid-cols-2 gap-3 md:grid-cols-4"
      >
        <div v-for="(s, k) in overview.pipeline" :key="k" class="bg-accent rounded-lg p-4">
          <div class="text-muted-foreground text-xs uppercase">{{ k }}</div>
          <div class="mt-1 text-2xl tabular-nums">{{ s.count }}</div>
          <div class="text-muted-foreground mt-1 text-xs">
            {{ $t('page.dfx.components.lastIngest') }}：
            {{ s.lastIngestAt ? new Date(s.lastIngestAt).toLocaleString() : '—' }}
          </div>
        </div>
      </div>
      <div v-else class="text-muted-foreground text-sm">暂无接入数据</div>
    </Card>
  </Page>
</template>
