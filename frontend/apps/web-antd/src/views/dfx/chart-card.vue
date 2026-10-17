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

import { onMounted, ref, watch } from 'vue';

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

// rows/chart 任一变化即重绘（父组件无需手动调 render）
watch([() => props.rows, () => props.chart], () => render(), { deep: true });
onMounted(() => render());

defineExpose({ render });
</script>

<template>
  <Card :title="chart.title" class="overflow-hidden">
    <EchartsUI ref="chartRef" height="260px" />
  </Card>
</template>
