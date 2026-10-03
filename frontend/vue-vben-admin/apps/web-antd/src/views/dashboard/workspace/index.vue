<script lang="ts" setup>
import type { DeviceAlertApi } from '#/api/device/alert';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { Button, Tag } from 'ant-design-vue';
import { useRouter } from 'vue-router';

import {
  getDeviceAlertList,
  updateDeviceAlert,
} from '#/api/device/alert';
import { getDeviceRuleList } from '#/api/device/rule';
import { $t } from '#/locales';

defineOptions({ name: 'DashboardWorkspace' });

const userStore = useUserStore();
const router = useRouter();

const alerts = ref<DeviceAlertApi.DeviceAlert[]>([]);
const stats = ref({ enabledRules: 0, totalRules: 0, unhandled: 0 });
const loading = ref(false);

const LEVEL_META: Record<string, { color: string; label: string }> = {
  error: { color: 'error', label: $t('device.levelError') },
  info: { color: 'processing', label: $t('device.levelInfo') },
  warn: { color: 'warning', label: $t('device.levelWarn') },
};

/** 快捷入口 */
const quickEntries = [
  {
    icon: 'icon-[lucide--list]',
    path: '/device/list',
    title: $t('page.device.list'),
  },
  {
    icon: 'icon-[lucide--terminal]',
    path: '/device/debug',
    title: $t('page.device.debug'),
  },
  {
    icon: 'icon-[lucide--bell-ring]',
    path: '/device/alert',
    title: $t('page.device.alert'),
  },
  {
    icon: 'icon-[lucide--git-branch]',
    path: '/device/rule',
    title: $t('page.device.rule'),
  },
];

function goEntry(path: string) {
  router.push(path);
}

async function loadData() {
  loading.value = true;
  try {
    const [alertsUnhandled, alertsAll, rules] = await Promise.all([
      getDeviceAlertList({ page: 1, pageSize: 8, status: 0 }),
      getDeviceAlertList({ page: 1, pageSize: 8 }),
      getDeviceRuleList({ page: 1, pageSize: 200 }),
    ]);
    // 未处理优先，不足 8 条用已处理补齐
    const merged: DeviceAlertApi.DeviceAlert[] = [];
    const seen = new Set<string>();
    for (const alert of [...alertsUnhandled.items, ...alertsAll.items]) {
      if (seen.has(alert.id)) continue;
      seen.add(alert.id);
      merged.push(alert);
      if (merged.length >= 8) break;
    }
    alerts.value = merged;
    stats.value = {
      enabledRules: rules.items.filter((item) => item.status === 1).length,
      totalRules: rules.total,
      unhandled: alertsUnhandled.total,
    };
  } finally {
    loading.value = false;
  }
}

function markHandled(alert: DeviceAlertApi.DeviceAlert) {
  updateDeviceAlert(alert.id, { status: 1 }).then(() => {
    loadData();
  });
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Page title="工作台">
    <div class="flex flex-col gap-4">
      <!-- 问候 + 概要 -->
      <div class="bg-card flex flex-wrap items-center justify-between gap-4 rounded-lg p-5 shadow-sm">
        <div>
          <div class="text-foreground text-lg font-medium">
            你好，{{ userStore.userInfo?.realName }}
          </div>
          <div class="text-muted-foreground mt-1 text-sm">
            欢迎回到格物 IoT 管理台，今天有 {{ stats.unhandled }} 条告警待处理。
          </div>
        </div>
        <div class="flex gap-6">
          <div class="text-center">
            <div class="text-amber-500 text-2xl font-semibold tabular-nums">
              {{ stats.unhandled }}
            </div>
            <div class="text-muted-foreground text-xs">
              {{ $t('device.unhandledCount') }}
            </div>
          </div>
          <div class="text-center">
            <div class="text-emerald-500 text-2xl font-semibold tabular-nums">
              {{ stats.enabledRules }} / {{ stats.totalRules }}
            </div>
            <div class="text-muted-foreground text-xs">
              启用中规则
            </div>
          </div>
        </div>
      </div>

      <!-- 快捷入口 -->
      <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <div
          v-for="entry in quickEntries"
          :key="entry.path"
          class="bg-card hover:border-primary/40 flex cursor-pointer items-center gap-3 rounded-lg border border-transparent p-4 shadow-sm transition-all hover:shadow-md"
          @click="goEntry(entry.path)"
        >
          <div class="bg-primary/10 flex size-10 shrink-0 items-center justify-center rounded-lg">
            <span :class="entry.icon" class="text-primary size-5"></span>
          </div>
          <div class="text-foreground text-sm font-medium">{{ entry.title }}</div>
        </div>
      </div>

      <!-- 事件与消息 -->
      <div class="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <!-- 左：最新告警事件（未处理优先） -->
        <div class="bg-card rounded-lg p-4 shadow-sm xl:col-span-2">
          <div class="mb-3 flex items-center justify-between">
            <div class="text-foreground text-sm font-medium">
              {{ $t('page.device.alert') }}
            </div>
            <Button type="link" size="small" @click="goEntry('/device/alert')">
              更多
            </Button>
          </div>
          <div class="space-y-2">
            <div
              v-for="alert in alerts"
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
              <div class="flex shrink-0 items-center gap-2">
                <span class="text-muted-foreground text-xs">
                  {{ alert.createTime }}
                </span>
                <Button
                  v-if="alert.status === 0"
                  type="link"
                  size="small"
                  @click="markHandled(alert)"
                >
                  {{ $t('device.markHandled') }}
                </Button>
              </div>
            </div>
          </div>
        </div>

        <!-- 右：系统消息 -->
        <div class="bg-card rounded-lg p-4 shadow-sm">
          <div class="text-foreground mb-3 text-sm font-medium">系统消息</div>
          <div class="space-y-2">
            <div class="bg-accent/30 rounded-md p-3 text-xs">
              <div class="text-foreground font-medium">平台功能上线</div>
              <div class="text-muted-foreground mt-1">
                产品 / 设备 / 物模型全链路已打通，支持 DSL 导入导出。
              </div>
            </div>
            <div class="bg-accent/30 rounded-md p-3 text-xs">
              <div class="text-foreground font-medium">设备接入提示</div>
              <div class="text-muted-foreground mt-1">
                设备上线后可在「在线调试」进行属性读写与方法调用。
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Page>
</template>
