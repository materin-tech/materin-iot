<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Checkbox, Input, message, Modal, Select } from 'ant-design-vue';

import {
  addOtaTaskDevices, createOtaTask, getOtaCandidates, getOtaPackages,
  getOtaTaskDevices, getOtaTasks, OTA_STATUS_TEXT, pauseOtaTask,
  resumeOtaTask, retryOtaTaskDevice, startOtaTask, terminateOtaTask,
  getProductList,
} from '#/api';

defineOptions({ name: 'OtaTasks' });

const TASK_STATUS: Record<number, { color: string; text: string }> = {
  0: { color: '#9ca3af', text: '未启动' },
  1: { color: '#1677ff', text: '进行中' },
  2: { color: '#f59e0b', text: '已暂停' },
  3: { color: '#ef4444', text: '已终止' },
  4: { color: '#10b981', text: '已完成' },
};

const rows = ref<any[]>([]);
const packages = ref<any[]>([]);
const products = ref<any[]>([]);
const addOpen = ref(false);
const detailOpen = ref(false);
const detailTask = ref<any>();
const detailRows = ref<any[]>([]);
const detailTotal = ref(0);
const detailPage = reactive({ page: 1, pageSize: 10 });
const candRows = ref<any[]>([]);
const candTotal = ref(0);
const candPage = reactive({ page: 1, pageSize: 10 });
const candKeyword = ref('');
const candChecked = ref<number[]>([]);
const form = reactive({
  packageName: '',
  productId: undefined as number | undefined,
  scope: 'device' as 'device' | 'product',
  taskName: '',
});
let timer: ReturnType<typeof setInterval> | undefined;

async function load() {
  const data = (await getOtaTasks({ page: 1, pageSize: 50 })) as any;
  rows.value = data?.items ?? [];
}

function openDetail(t: any) {
  detailTask.value = t;
  detailPage.page = 1;
  candPage.page = 1;
  candChecked.value = [];
  loadDetail();
  loadCandidates();
}

function toggleCand(id: number, e: any) {
  candChecked.value = e.target.checked
    ? [...candChecked.value, id]
    : candChecked.value.filter((v) => v !== id);
}

async function retry(id?: number) {
  if (!id) return;
  await retryOtaTaskDevice(id);
  message.success('已重新推送');
  await loadDetail();
}

async function loadDetail() {
  if (!detailTask.value) return;
  const data = (await getOtaTaskDevices(detailTask.value.id, detailPage)) as any;
  detailRows.value = data?.items ?? [];
  detailTotal.value = data?.total ?? 0;
}

async function loadCandidates() {
  if (!detailTask.value) return;
  const data = (await getOtaCandidates(detailTask.value.id, {
    keyword: candKeyword.value || undefined,
    page: candPage.page,
    pageSize: candPage.pageSize,
  })) as any;
  candRows.value = data?.items ?? [];
  candTotal.value = data?.total ?? 0;
}

async function submit() {
  const pkg = packages.value.find((p) => p.name === form.packageName);
  if (!pkg) {
    message.warning('请选择固件包');
    return;
  }
  await createOtaTask({
    packageName: form.packageName,
    productId: form.productId,
    taskName: form.taskName || 'OTA 升级计划',
  } as any);
  addOpen.value = false;
  message.success('计划已创建（未启动）。打开详情添加设备后再启动。');
  await load();
}

async function addDevices() {
  if (candChecked.value.length === 0) {
    message.warning('请先勾选设备');
    return;
  }
  const r = (await addOtaTaskDevices(detailTask.value.id, candChecked.value)) as any;
  message.success(`已添加 ${r.added} 台`);
  candChecked.value = [];
  await Promise.all([loadCandidates(), loadDetail(), load()]);
}

async function act(taskId: number, action: 'start' | 'pause' | 'resume' | 'terminate', tip: string) {
  const map = {
    pause: pauseOtaTask, resume: resumeOtaTask,
    start: startOtaTask, terminate: terminateOtaTask,
  };
  await map[action](taskId);
  message.success(tip);
  await load();
}

onMounted(async () => {
  await load();
  const [pk, pr] = await Promise.all([
    getOtaPackages({ page: 1, pageSize: 100 }),
    getProductList({ page: 1, pageSize: 200 }),
  ]);
  packages.value = (pk as any)?.items ?? [];
  products.value = (pr as any)?.items ?? [];
  timer = setInterval(load, 30_000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <Page :title="$t('page.ota.tasks.title')">
    <div class="mb-4 flex items-center gap-3">
      <Button type="primary" @click="addOpen = true">新建升级计划</Button>
    </div>
    <div class="rounded-lg border">
      <table class="w-full text-sm">
        <thead class="bg-accent text-left">
          <tr>
            <th class="px-4 py-2">计划</th>
            <th class="px-4 py-2">范围</th>
            <th class="px-4 py-2">设备数</th>
            <th class="px-4 py-2">状态</th>
            <th class="px-4 py-2">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in rows" :key="t.id" class="border-t">
            <td class="px-4 py-2">
              {{ t.taskName }} (#{{ t.id }})
              <span class="text-muted-foreground ml-1 text-xs">
                {{ t.scope === 'product' ? `按产品：${t.productName || t.productId}` : '按设备' }}
              </span>
            </td>
            <td class="px-4 py-2 text-xs">{{ t.scope === 'product' ? '按产品统一' : '按设备单独' }}</td>
            <td class="px-4 py-2 tabular-nums">{{ t.deviceCount }}</td>
            <td class="px-4 py-2" :style="{ color: TASK_STATUS[t.status]?.color }">
              {{ TASK_STATUS[t.status]?.text ?? t.status }}
            </td>
            <td class="px-4 py-2 space-x-1">
              <Button size="small" @click="openDetail(t)">详情</Button>
              <Button v-if="t.status === 0 || t.status === 2" size="small" type="primary" @click="act(t.id, 'start', '已启动')">启动</Button>
              <Button v-if="t.status === 1" size="small" @click="act(t.id, 'pause', '已暂停')">暂停</Button>
              <Button v-if="t.status === 2" size="small" type="primary" @click="act(t.id, 'resume', '已恢复')">恢复</Button>
              <Button v-if="t.status === 0 || t.status === 1 || t.status === 2" danger size="small" @click="act(t.id, 'terminate', '已终止')">终止</Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal v-model:open="addOpen" title="新建升级计划" @ok="submit">
      <div class="space-y-3">
        <Input v-model:value="form.taskName" placeholder="计划名称（可空）" />
        <Select v-model:value="form.packageName" class="w-full" placeholder="选择固件包" show-search
          :options="packages.map((p) => ({ label: `${p.name} v${p.version} (${p.productName})`, value: p.name }))" />
        <Select v-model:value="form.scope" class="w-full" placeholder="升级范围"
          :options="[{ label: '按设备单独升级（详情中添加）', value: 'device' }, { label: '按产品统一升级（详情中添加，限该产品设备）', value: 'product' }]" />
        <Select v-if="form.scope === 'product'" v-model:value="form.productId" class="w-full" placeholder="选择产品" show-search
          :options="products.map((p) => ({ label: p.name, value: Number(p.id) }))" />
        <div class="text-muted-foreground text-xs">
          计划创建后为"未启动"，在详情中分页添加设备，启动后系统分批推送（不会一次性全量加载设备）
        </div>
      </div>
    </Modal>

    <Modal v-model:open="detailOpen" :footer="null" :title="`升级计划详情 #${detailTask?.id}（${detailTask?.taskName || ''}）`" width="860px">
      <div class="space-y-4">
        <div class="rounded-lg border p-3">
          <div class="mb-2 text-sm font-medium">添加设备（分页候选，scope=product 已限产品；已添加的自动排除）</div>
          <div class="mb-2 flex gap-2">
            <Input v-model:value="candKeyword" class="flex-1" placeholder="按设备标识搜索" @press-enter="() => { candPage.page = 1; loadCandidates(); }" />
            <Button @click="() => { candPage.page = 1; loadCandidates(); }">搜索</Button>
            <Button type="primary" :disabled="candChecked.length === 0" @click="addDevices">
              添加选中（{{ candChecked.length }}）
            </Button>
          </div>
          <table class="w-full text-sm">
            <thead class="bg-accent text-left"><tr>
              <th class="w-10"></th><th class="px-2 py-1">设备</th>
              <th class="px-2 py-1">标识</th><th class="px-2 py-1">当前固件</th>
            </tr></thead>
            <tbody>
              <tr v-for="d in candRows" :key="d.id" class="border-t">
                <td class="px-2 py-1"><Checkbox :checked="candChecked.includes(d.id)" @change="(e) => toggleCand(d.id, e)" /></td>
                <td class="px-2 py-1">{{ d.name }}</td>
                <td class="px-2 py-1 font-mono text-xs">{{ d.deviceKey }}</td>
                <td class="px-2 py-1 text-xs">{{ d.firmware || '—' }}</td>
              </tr>
              <tr v-if="candRows.length === 0"><td colspan="4" class="px-2 py-3 text-center text-gray-400">无候选设备</td></tr>
            </tbody>
          </table>
          <div class="mt-2 flex items-center gap-2 text-xs">
            <Button size="small" :disabled="candPage.page <= 1" @click="() => { candPage.page--; loadCandidates(); }">上一页</Button>
            <span class="tabular-nums">{{ candPage.page }} / {{ Math.max(1, Math.ceil(candTotal / candPage.pageSize)) }}（共 {{ candTotal }}）</span>
            <Button size="small" :disabled="candPage.page >= Math.ceil(candTotal / candPage.pageSize)" @click="() => { candPage.page++; loadCandidates(); }">下一页</Button>
          </div>
        </div>

        <div class="rounded-lg border p-3">
          <div class="mb-2 flex items-center justify-between">
            <span class="text-sm font-medium">已加入计划（{{ detailTotal }} 台）</span>
            <Button size="small" @click="loadDetail">刷新</Button>
          </div>
          <table class="w-full text-sm">
            <thead class="bg-accent text-left"><tr>
              <th class="px-2 py-1">设备</th><th class="px-2 py-1">状态</th>
              <th class="px-2 py-1">进度</th><th class="px-2 py-1">消息</th><th class="px-2 py-1">操作</th>
            </tr></thead>
            <tbody>
              <tr v-for="d in detailRows" :key="d.id" class="border-t">
                <td class="px-2 py-1">{{ d.deviceName }} ({{ d.deviceKey }})</td>
                <td class="px-2 py-1" :style="{ color: TASK_STATUS[d.status]?.color }">
                  {{ OTA_STATUS_TEXT[d.status] }}
                </td>
                <td class="px-2 py-1 tabular-nums">{{ d.progress }}%</td>
                <td class="px-2 py-1 text-xs">{{ d.message || '-' }}</td>
                <td class="px-2 py-1">
                  <Button v-if="d.status === 5" danger size="small" @click="retry(d.id)">重推</Button>
                </td>
              </tr>
              <tr v-if="detailRows.length === 0"><td colspan="5" class="px-2 py-3 text-center text-gray-400">尚未添加设备</td></tr>
            </tbody>
          </table>
          <div class="mt-2 flex items-center gap-2 text-xs">
            <Button size="small" :disabled="detailPage.page <= 1" @click="() => { detailPage.page--; loadDetail(); }">上一页</Button>
            <span class="tabular-nums">{{ detailPage.page }} / {{ Math.max(1, Math.ceil(detailTotal / detailPage.pageSize)) }}</span>
            <Button size="small" :disabled="detailPage.page >= Math.ceil(detailTotal / detailPage.pageSize)" @click="() => { detailPage.page++; loadDetail(); }">下一页</Button>
          </div>
        </div>
      </div>
    </Modal>
  </Page>
</template>
