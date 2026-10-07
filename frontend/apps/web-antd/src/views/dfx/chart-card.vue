<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';
import type { DfxApi } from '#/api/device/dfx';

import { ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { Card } from 'ant-design-vue';

defineOptions({ name: 'DfxChartCard' });

const props = defineProps<{
  chart: DfxApi.DfxChartConfig;
  rows: Record<string, any>[];
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const COLORS = ['#1677ff', '#10b981', '#f59e0b', '#a855f7', '#ef4444', '#06b6d4'];

function render() {
  const series = props.chart.metrics.map((m, i) => ({
    name: m.name,
    type: (props.chart.type ?? 'line') as 'line' | 'bar',
    smooth: true,
    showSymbol: false,
    data: props.rows
      .filter((r) => r[m.key] !== null && r[m.key] !== undefined && r[m.key] !== '')
      .map((r) => [Number(r.time), Number(r[m.key])]),
    itemStyle: { color: COLORS[i % COLORS.length] },
  }));
  renderEcharts({
    grid: { bottom: 30, left: 44, right: 16, top: 36 },
    legend: { bottom: 0 },
    series,
    title: { left: 'center', text: props.chart.title, textStyle: { fontSize: 13 } },
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'time' as const },
    yAxis: {
      max: props.chart.yMax,
      name: props.chart.unit,
      type: 'value' as const,
    },
  });
}

defineExpose({ render });
</script>

<template>
  <Card :title="chart.title" class="overflow-hidden">
    <EchartsUI ref="chartRef" height="260px" />
  </Card>
</template>
