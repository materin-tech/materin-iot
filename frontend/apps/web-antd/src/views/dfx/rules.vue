<script setup lang="ts">
import type { DfxApi } from '#/api/device/dfx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Button, Input, message, Modal, Select } from 'ant-design-vue';

import {
  createDfxRule, deleteDfxRule, getDfxMetricList, getDeviceList, getDfxRuleList,
} from '#/api';
import { $t } from '#/locales';

defineOptions({ name: 'DfxRules' });

interface RuleRow {
  id?: number;
  deviceId: number;
  deviceName: string;
  metric: string;
  comparator: string;
  threshold: number;
  level: string;
  suppressSeconds?: number;
  enabled?: number;
}

const rows = ref<RuleRow[]>([]);
const total = ref(0);
const query = reactive({ page: 1, pageSize: 20 });
const metricDict = ref<DfxApi.DfxMetricConfig[]>([]);
const devices = ref<any[]>([]);
const addOpen = ref(false);
const form = reactive({
  comparator: 'gt',
  deviceId: undefined as number | undefined,
  level: 'warn',
  metric: undefined as string | undefined,
  suppressSeconds: 600,
  suppressText: '600',
  thresholdText: '',
});

async function load() {
  const data = (await getDfxRuleList(query)) as any;
  rows.value = data?.items ?? [];
  total.value = data?.total ?? 0;
}

async function loadDict() {
  metricDict.value = (await getDfxMetricList()) ?? [];
}

async function loadDevices() {
  const data = await getDeviceList({ page: 1, pageSize: 500 });
  devices.value = (data as any)?.items ?? [];
}

async function submit() {
  if (!form.deviceId || !form.metric || !form.thresholdText) {
    message.warning('请完整填写设备/指标/阈值');
    return;
  }
  const device = devices.value.find((d) => Number(d.id) === form.deviceId);
  await createDfxRule({
    comparator: form.comparator,
    deviceId: form.deviceId,
    deviceName: device?.name ?? '',
    level: form.level,
    metric: form.metric,
    suppressSeconds: Number(form.suppressText) || 600,
    threshold: Number(form.thresholdText),
  });
  addOpen.value = false;
  message.success('已创建');
  await load();
}

async function remove(id?: number) {
  if (!id) return;
  await deleteDfxRule(id);
  message.success('已删除');
  await load();
}

onMounted(async () => {
  await Promise.all([load(), loadDict(), loadDevices()]);
});
</script>

<template>
  <Page :title="$t('page.dfx.rules.title')">
    <div class="mb-4 flex items-center gap-3">
      <Button type="primary" @click="addOpen = true">新增规则</Button>
    </div>
    <div class="rounded-lg border">
      <table class="w-full text-sm">
        <thead class="bg-accent text-left">
          <tr>
            <th class="px-4 py-2">设备</th>
            <th class="px-4 py-2">指标</th>
            <th class="px-4 py-2">条件</th>
            <th class="px-4 py-2">级别</th>
            <th class="px-4 py-2">抑制(秒)</th>
            <th class="px-4 py-2">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id" class="border-t">
            <td class="px-4 py-2">{{ r.deviceName || r.deviceId }}</td>
            <td class="px-4 py-2">{{ r.metric }}</td>
            <td class="px-4 py-2">{{ r.comparator === 'gt' ? '>' : '<' }} {{ r.threshold }}</td>
            <td class="px-4 py-2">{{ r.level }}</td>
            <td class="px-4 py-2">{{ r.suppressSeconds }}</td>
            <td class="px-4 py-2">
              <Button danger size="small" @click="remove(r.id)">删除</Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal v-model:open="addOpen" title="新增规则" @ok="submit">
      <div class="space-y-3">
        <Select v-model:value="form.deviceId" :options="devices.map((d) => ({ label: `${d.name} (${d.deviceKey})`, value: Number(d.id) }))" class="w-full" placeholder="设备" show-search />
        <Select v-model:value="form.metric" :options="metricDict.map((c) => ({ label: `${c.name} (${c.metric})`, value: c.metric }))" class="w-full" placeholder="指标（字典即中文口径）" show-search />
        <div class="flex gap-2">
          <Select v-model:value="form.comparator" class="w-24" :options="[{ label: '大于', value: 'gt' }, { label: '小于', value: 'lt' }]">
          </Select>
          <Input v-model:value="form.thresholdText" class="flex-1" placeholder="阈值" />
        </div>
        <div class="flex gap-2">
          <Select v-model:value="form.level" class="w-32" :options="[{ label: '信息', value: 'info' }, { label: '警告', value: 'warn' }, { label: '故障', value: 'error' }]">
          </Select>
          <Input v-model:value="form.suppressText" class="flex-1" placeholder="抑制窗口（秒）" />
        </div>
      </div>
    </Modal>
  </Page>
</template>
