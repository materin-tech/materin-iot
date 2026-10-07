<script setup lang="ts">
import type { DfxApi } from '#/api/device/dfx';

import { onBeforeUnmount, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Button, Card } from 'ant-design-vue';

import { getDfxHealth } from '#/api/device';
import { $t } from '#/locales';

defineOptions({ name: 'DfxComponents' });

const health = ref<DfxApi.DfxHealthResponse>();
const loading = ref(false);

const STATUS_COLOR: Record<string, string> = {
  UP: '#10b981',
  DOWN: '#ef4444',
  DISABLED: '#9ca3af',
};

async function load() {
  loading.value = true;
  try {
    health.value = await getDfxHealth();
  } finally {
    loading.value = false;
  }
}

let timer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  load();
  timer = setInterval(load, 30_000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <Page :title="$t('page.dfx.components.title')">
    <div class="mb-4 flex justify-end">
      <Button :loading="loading" @click="load">{{ $t('page.dfx.common.refresh') }}</Button>
    </div>
    <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
      <Card v-for="c in health?.components ?? []" :key="c.name" class="overflow-hidden">
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
        v-if="health?.pipeline && Object.keys(health.pipeline).length"
        class="grid grid-cols-2 gap-3 md:grid-cols-4"
      >
        <div v-for="(s, k) in health.pipeline" :key="k" class="bg-accent rounded-lg p-4">
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
