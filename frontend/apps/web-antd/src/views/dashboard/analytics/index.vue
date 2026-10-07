<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { DeviceAlertApi } from '#/api/device/alert';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Tag } from 'ant-design-vue';

import { getDeviceAlertList } from '#/api/device/alert';
import { getDeviceList } from '#/api/device/device';
import { getDeviceRuleList } from '#/api/device/rule';
import { getProductList } from '#/api/product/product';
import { getThingModelList } from '#/api/product/thing-model';
import { $t } from '#/locales';

defineOptions({ name: 'DashboardAnalytics' });

const stats = ref({
  models: 0,
  online: 0,
  products: 0,
  rules: 0,
  total: 0,
  unhandledAlerts: 0,
});

const recentAlerts = ref<DeviceAlertApi.DeviceAlert[]>([]);

const deviceChartRef = ref<EchartsUIType>();
const alertChartRef = ref<EchartsUIType>();
const protocolChartRef = ref<EchartsUIType>();

const { renderEcharts: renderDeviceChart } = useEcharts(deviceChartRef);
const { renderEcharts: renderAlertChart } = useEcharts(alertChartRef);
const { renderEcharts: renderProtocolChart } = useEcharts(protocolChartRef);

const LEVEL_META: Record<string, { color: string; label: string }> = {
  error: { color: 'error', label: $t('device.levelError') },
  info: { color: 'processing', label: $t('device.levelInfo') },
  warn: { color: 'warning', label: $t('device.levelWarn') },
};

async function loadData() {
  const [
    devices,
    devicesOnline,
    products,
    models,
    alertsAll,
    alertsUnhandled,
    alertInfo,
    alertWarn,
    alertError,
    rules,
  ] = await Promise.all([
    getDeviceList({ page: 1, pageSize: 1 }),
    getDeviceList({ page: 1, pageSize: 1, status: 1 }),
    getProductList({ page: 1, pageSize: 200 }),
    getThingModelList({ page: 1, pageSize: 1 }),
    getDeviceAlertList({ page: 1, pageSize: 6 }),
    getDeviceAlertList({ page: 1, pageSize: 1, status: 0 }),
    getDeviceAlertList({ page: 1, pageSize: 1, level: 'info' }),
    getDeviceAlertList({ page: 1, pageSize: 1, level: 'warn' }),
    getDeviceAlertList({ page: 1, pageSize: 1, level: 'error' }),
    getDeviceRuleList({ page: 1, pageSize: 200 }),
  ]);

  stats.value = {
    models: models.total,
    online: devicesOnline.total,
    products: products.total,
    rules: rules.items.filter((item) => item.status === 1).length,
    total: devices.total,
    unhandledAlerts: alertsUnhandled.total,
  };

  recentAlerts.value = alertsAll.items;

  const offline =
    (await getDeviceList({ page: 1, pageSize: 1, status: 2 })).total;
  const inactive =
    (await getDeviceList({ page: 1, pageSize: 1, status: 0 })).total;

  // 设备状态分布（环形图）
  renderDeviceChart({
    series: [
      {
        avoidLabelOverlap: true,
        data: [
          { itemStyle: { color: '#10b981' }, name: $t('device.statusActive'), value: devicesOnline.total },
          { itemStyle: { color: '#ef4444' }, name: $t('device.statusOffline'), value: offline },
          { itemStyle: { color: '#9ca3af' }, name: $t('device.statusInactive'), value: inactive },
        ],
        label: { show: true, formatter: '{b}: {c}' },
        radius: ['45%', '70%'],
        type: 'pie',
      },
    ],
    tooltip: { trigger: 'item' },
  });

  // 告警级别分布（柱状图）
  renderAlertChart({
    grid: { bottom: 30, left: 40, right: 16, top: 30 },
    series: [
      {
        barWidth: 36,
        data: [
          { itemStyle: { color: '#3b82f6' }, value: alertInfo.total },
          { itemStyle: { color: '#f59e0b' }, value: alertWarn.total },
          { itemStyle: { color: '#ef4444' }, value: alertError.total },
        ],
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        type: 'bar',
      },
    ],
    tooltip: { trigger: 'axis' },
    xAxis: {
      axisLabel: { fontSize: 10 },
      data: [$t('device.levelInfo'), $t('device.levelWarn'), $t('device.levelError')],
      type: 'category',
    },
    yAxis: { minInterval: 1, type: 'value' },
  });

  // 产品接入协议分布（柱状图）
  const protocolCount: Record<string, number> = {};
  products.items.forEach((item) => {
    const key = item.protocol || 'MQTT';
    protocolCount[key] = (protocolCount[key] ?? 0) + 1;
  });
  renderProtocolChart({
    grid: { bottom: 30, left: 40, right: 16, top: 30 },
    series: [
      {
        barWidth: 28,
        data: Object.values(protocolCount),
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: '#1677ff',
        },
        type: 'bar',
      },
    ],
    tooltip: { trigger: 'axis' },
    xAxis: {
      axisLabel: { fontSize: 10 },
      data: Object.keys(protocolCount),
      type: 'category',
    },
    yAxis: { minInterval: 1, type: 'value' },
  });
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Page title="分析页">
    <div class="flex flex-col gap-4">
      <!-- 系统状态统计 -->
      <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <div
          v-for="tile in [
            { key: 'total', icon: 'icon-[lucide--cpu]', label: $t('device.totalDevices'), sub: `${$t('device.onlineCount')} ${stats.online}`, valueClass: 'text-primary' },
            { key: 'products', icon: 'icon-[lucide--package]', label: $t('product.totalProducts'), sub: `${$t('device.ruleStatus')} ${stats.rules}`, valueClass: 'text-emerald-500' },
            { key: 'models', icon: 'icon-[lucide--boxes]', label: $t('product.thingModel.total'), sub: '', valueClass: 'text-blue-500' },
            { key: 'unhandledAlerts', icon: 'icon-[lucide--bell-ring]', label: $t('device.unhandledCount'), sub: $t('page.device.alert'), valueClass: 'text-red-500' },
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
            <div v-if="tile.sub" class="text-muted-foreground mt-1 text-xs">
              {{ tile.sub }}
            </div>
          </div>
        </div>
      </div>

      <!-- 图表区 -->
      <div class="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div class="bg-card rounded-lg p-4 shadow-sm">
          <div class="text-foreground mb-2 text-sm font-medium">
            {{ $t('device.status') }}
          </div>
          <EchartsUI ref="deviceChartRef" height="240px" />
        </div>
        <div class="bg-card rounded-lg p-4 shadow-sm">
          <div class="text-foreground mb-2 text-sm font-medium">
            {{ $t('device.level') }}
          </div>
          <EchartsUI ref="alertChartRef" height="240px" />
        </div>
        <div class="bg-card rounded-lg p-4 shadow-sm">
          <div class="text-foreground mb-2 text-sm font-medium">
            {{ $t('product.protocol') }}
          </div>
          <EchartsUI ref="protocolChartRef" height="240px" />
        </div>
      </div>

      <!-- 最新告警 -->
      <div class="bg-card rounded-lg p-4 shadow-sm">
        <div class="mb-3 flex items-center justify-between">
          <div class="text-foreground text-sm font-medium">
            {{ $t('page.device.alert') }}
          </div>
        </div>
        <div class="space-y-2">
          <div
            v-for="alert in recentAlerts"
            :key="alert.id"
            class="bg-accent/30 flex items-center justify-between gap-3 rounded-md p-2.5"
          >
            <div class="flex min-w-0 items-center gap-2">
              <Tag :color="LEVEL_META[alert.level]?.color" class="!m-0 shrink-0">
                {{ LEVEL_META[alert.level]?.label }}
              </Tag>
              <span class="text-foreground truncate text-sm">
                {{ alert.content }}
              </span>
              <span class="text-muted-foreground shrink-0 text-xs">
                {{ alert.deviceName }}
              </span>
            </div>
            <span class="text-muted-foreground shrink-0 text-xs">
              {{ alert.createTime }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </Page>
</template>
