<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Input, message, Modal, Select } from 'ant-design-vue';

import {
  createOtaTask, getOtaPackages, getOtaTaskDevices, getOtaTasks, getDeviceList,
  OTA_STATUS_TEXT, retryOtaTaskDevice,
} from '#/api';

defineOptions({ name: 'OtaTasks' });

const rows = ref<any[]>([]);
const packages = ref<any[]>([]);
const devices = ref<any[]>([]);
const addOpen = ref(false);
const detailOpen = ref(false);
const detailRows = ref<any[]>([]);
const detailTaskId = ref<number>();
const form = reactive({
  deviceIds: [] as number[],
  packageName: '',
  taskName: '',
});
let timer: ReturnType<typeof setInterval> | undefined;

const STATUS_COLOR: Record<number, string> = {
  0: '#9ca3af', 1: '#1677ff', 2: '#1677ff', 3: '#f59e0b', 4: '#10b981', 5: '#ef4444',
};

async function load() {
  const data = (await getOtaTasks({ page: 1, pageSize: 20 })) as any;
  rows.value = data?.items ?? [];
}

async function submit() {
  const pkg = packages.value.find((p) => p.name === form.packageName);
  if (!pkg || form.deviceIds.length === 0) {
    message.warning('请选择固件包并勾选设备');
    return;
  }
  await createOtaTask({ deviceIds: form.deviceIds, packageId: Number(pkg.id), taskName: form.taskName || 'OTA 升级' });
  addOpen.value = false;
  message.success('任务已创建并推送');
  await load();
}

async function showDetail(taskId: number) {
  detailTaskId.value = taskId;
  const data = (await getOtaTaskDevices(taskId, { page: 1, pageSize: 100 })) as any;
  detailRows.value = data?.items ?? [];
  detailOpen.value = true;
}

async function retry(id?: number) {
  if (!id) return;
  await retryOtaTaskDevice(id);
  message.success('已重新推送');
  if (detailTaskId.value) await showDetail(detailTaskId.value);
}

onMounted(async () => {
  await load();
  const [pk, dv] = await Promise.all([
    getOtaPackages({ page: 1, pageSize: 100 }),
    getDeviceList({ page: 1, pageSize: 500 }),
  ]);
  packages.value = (pk as any)?.items ?? [];
  devices.value = (dv as any)?.items ?? [];
  timer = setInterval(load, 30_000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <Page :title="$t('page.ota.tasks.title')">
    <div class="mb-4 flex items-center gap-3">
      <Button type="primary" @click="addOpen = true">新建升级任务</Button>
    </div>
    <div class="rounded-lg border">
      <table class="w-full text-sm">
        <thead class="bg-accent text-left">
          <tr>
            <th class="px-4 py-2">任务</th>
            <th class="px-4 py-2">设备数</th>
            <th class="px-4 py-2">状态</th>
            <th class="px-4 py-2">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in rows" :key="t.id" class="border-t">
            <td class="px-4 py-2">{{ t.taskName }} (#{{ t.id }})</td>
            <td class="px-4 py-2 tabular-nums">{{ t.deviceCount }}</td>
            <td class="px-4 py-2">{{ t.status === 1 ? '已完成' : t.status === 2 ? '已取消' : '进行中' }}</td>
            <td class="px-4 py-2"><Button size="small" @click="showDetail(t.id)">明细</Button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal v-model:open="detailOpen" :footer="null" title="升级明细" width="720px">
      <table class="w-full text-sm">
        <thead class="bg-accent text-left">
          <tr>
            <th class="px-3 py-2">设备</th>
            <th class="px-3 py-2">状态</th>
            <th class="px-3 py-2">进度</th>
            <th class="px-3 py-2">消息</th>
            <th class="px-3 py-2">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in detailRows" :key="d.id" class="border-t">
            <td class="px-3 py-2">{{ d.deviceName }} ({{ d.deviceKey }})</td>
            <td class="px-3 py-2" :style="{ color: STATUS_COLOR[d.status] }">
              {{ OTA_STATUS_TEXT[d.status] }}
            </td>
            <td class="px-3 py-2 tabular-nums">{{ d.progress }}%</td>
            <td class="px-3 py-2 text-xs">{{ d.message || '-' }}</td>
            <td class="px-3 py-2">
              <Button v-if="d.status === 5" danger size="small" @click="retry(d.id)">重推</Button>
            </td>
          </tr>
        </tbody>
      </table>
    </Modal>

    <Modal v-model:open="addOpen" title="新建升级任务" @ok="submit">
      <div class="space-y-3">
        <Input v-model:value="form.taskName" placeholder="任务名称（可空）" />
        <Select v-model:value="form.packageName" class="w-full" placeholder="选择固件包" show-search
          :options="packages.map((p) => ({ label: `${p.name} v${p.version} (${p.productName})`, value: p.name }))" />
        <Select v-model:value="form.deviceIds" class="w-full" placeholder="勾选升级设备" mode="multiple" show-search
          :options="devices.map((d) => ({ label: `${d.name} (${d.deviceKey})`, value: Number(d.id) }))" />
      </div>
    </Modal>
  </Page>
</template>
