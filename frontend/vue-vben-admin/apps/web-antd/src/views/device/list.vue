<script lang="ts" setup>
import type { DeviceApi } from '#/api/device/device';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Button,
  Input,
  message,
  Modal,
  Pagination,
  Select,
  Tag,
  Upload,
} from 'ant-design-vue';

import {
  createDevice,
  deleteDevice,
  getDeviceList,
  importDevices,
  updateDevice,
} from '#/api/device/device';
import { getProductList } from '#/api/product/product';
import { $t } from '#/locales';
import { downloadJsonFile, readTextFile } from '#/utils/file-download';

defineOptions({ name: 'DeviceList' });

const InputSearch = Input.Search;

const items = ref<DeviceApi.Device[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({
  keyword: '',
  page: 1,
  pageSize: 12,
  productId: '',
  status: '',
});
/** 勾选的设备 ID（批量导出用） */
const selectedIds = ref<string[]>([]);
const stats = ref({ inactive: 0, offline: 0, online: 0, total: 0 });

const STATUS_META: Record<
  number,
  { color: string; label: string; tile: string }
> = {
  0: {
    color: 'default',
    label: $t('device.statusInactive'),
    tile: 'text-muted-foreground',
  },
  1: {
    color: 'success',
    label: $t('device.statusActive'),
    tile: 'text-emerald-500',
  },
  2: {
    color: 'error',
    label: $t('device.statusOffline'),
    tile: 'text-red-500',
  },
};

async function fetchList() {
  loading.value = true;
  try {
    const res = await getDeviceList({
      keyword: query.keyword || undefined,
      page: query.page,
      pageSize: query.pageSize,
      productId: query.productId || undefined,
      status: query.status === '' ? undefined : query.status,
    });
    items.value = res.items;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

async function loadStats() {
  const [all, online, offline, inactive] = await Promise.all([
    getDeviceList({ page: 1, pageSize: 1 }),
    getDeviceList({ page: 1, pageSize: 1, status: 1 }),
    getDeviceList({ page: 1, pageSize: 1, status: 2 }),
    getDeviceList({ page: 1, pageSize: 1, status: 0 }),
  ]);
  stats.value = {
    inactive: inactive.total,
    offline: offline.total,
    online: online.total,
    total: all.total,
  };
}

async function refresh() {
  await Promise.all([fetchList(), loadStats()]);
}

function onSearch(keyword: string) {
  query.keyword = keyword;
  query.page = 1;
  fetchList();
}

function onFilterChange() {
  query.page = 1;
  fetchList();
}

function onPageChange(page: number, pageSize: number) {
  query.page = page;
  query.pageSize = pageSize;
  fetchList();
}

/** 条目 → 导出结构 */
function toExport(row: DeviceApi.Device) {
  return {
    name: row.name,
    deviceKey: row.deviceKey,
    productName: row.productName,
    firmware: row.firmware,
    remark: row.remark,
  };
}

function exportRows(rows: DeviceApi.Device[], suffix = '') {
  downloadJsonFile(
    {
      type: 'materin-devices',
      exportedAt: new Date().toISOString(),
      items: rows.map((row) => toExport(row)),
    },
    `materin-devices${suffix}-${new Date().toISOString().slice(0, 10)}.json`,
  );
}

function onExportSelected() {
  const rows = items.value.filter((row) => selectedIds.value.includes(row.id));
  if (rows.length === 0) {
    message.warning($t('product.exportSelectedEmpty'));
    return;
  }
  exportRows(rows, '-selected');
}

async function onExportAll() {
  const res = await getDeviceList({ page: 1, pageSize: 500 });
  exportRows(res.items);
}

function toggleSelect(id: string) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id];
}

// ---------- 新增/编辑 ----------
const productOptions = ref<{ label: string; value: string }[]>([]);

async function loadProductOptions() {
  if (productOptions.value.length > 0) return;
  const res = await getProductList({ page: 1, pageSize: 200 });
  productOptions.value = res.items.map((item) => ({
    label: item.name,
    value: item.id,
  }));
}

const formOpen = ref(false);
const formMode = ref<'create' | 'edit'>('create');
const formData = reactive<{ deviceKey?: string; id?: string; name: string; productId?: string; remark: string }>({
  deviceKey: '',
  id: undefined,
  name: '',
  productId: undefined,
  remark: '',
});

function onCreate() {
  formMode.value = 'create';
  Object.assign(formData, { deviceKey: '', id: undefined, name: '', productId: undefined, remark: '' });
  formOpen.value = true;
}

function onEdit(row: DeviceApi.Device) {
  formMode.value = 'edit';
  Object.assign(formData, {
    deviceKey: row.deviceKey,
    id: row.id,
    name: row.name,
    productId: row.productId,
    remark: row.remark ?? '',
  });
  formOpen.value = true;
}

const formTitle = computed(() =>
  formMode.value === 'edit'
    ? $t('common.edit', $t('page.device.list'))
    : $t('common.create', $t('page.device.list')),
);

async function onSave() {
  if (!formData.name.trim() || !formData.productId) {
    message.error(
      $t('device.name') + ' / ' + $t('device.product') + ' 必填',
    );
    return;
  }
  const payload: Record<string, any> = {
    name: formData.name,
    productId: formData.productId,
    remark: formData.remark,
  };
  if (formData.deviceKey?.trim()) {
    payload.deviceKey = formData.deviceKey.trim();
  }
  if (formMode.value === 'edit') {
    await updateDevice(formData.id!, payload);
  } else {
    await createDevice(payload as any);
  }
  message.success($t('ui.actionMessage.operationSuccess'));
  formOpen.value = false;
  refresh();
}

function onDelete(row: DeviceApi.Device) {
  deleteDevice(row.id).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    refresh();
  });
}

// ---------- 导入 ----------
const importOpen = ref(false);
const importParsed = ref<any[]>([]);
const importError = ref('');

async function handleImportFile(file: File) {
  importParsed.value = [];
  importError.value = '';
  try {
    const data = JSON.parse(await readTextFile(file));
    const list = Array.isArray(data) ? data : (data.items ?? data.devices ?? []);
    importParsed.value = list.filter((item: any) => item?.name);
    if (importParsed.value.length === 0) {
      importError.value = $t('product.importEmpty');
    }
  } catch {
    importError.value = $t('product.importInvalid');
  }
  return false;
}

const importSummary = computed(() =>
  importParsed.value.length > 0
    ? $t('product.importParsed', [importParsed.value.length])
    : '',
);

async function onImportConfirm() {
  if (importParsed.value.length === 0) {
    importError.value = $t('product.importEmpty');
    return;
  }
  const res = await importDevices({ items: importParsed.value });
  message.success($t('product.importResult', [res.imported, res.failed]));
  importOpen.value = false;
  refresh();
}

onMounted(() => {
  refresh();
  loadProductOptions();
});
</script>

<template>
  <Page :title="$t('page.device.list')">
    <div class="flex flex-col gap-4">
      <!-- 统计条 -->
      <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <div
          v-for="tile in [
            { key: 'total', icon: 'icon-[lucide--cpu]', label: $t('device.totalDevices'), valueClass: 'text-primary' },
            { key: 'online', icon: 'icon-[lucide--wifi]', label: $t('device.onlineCount'), valueClass: 'text-emerald-500' },
            { key: 'offline', icon: 'icon-[lucide--wifi-off]', label: $t('device.offlineCount'), valueClass: 'text-red-500' },
            { key: 'inactive', icon: 'icon-[lucide--circle-slash]', label: $t('device.inactiveCount'), valueClass: 'text-muted-foreground' },
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
          </div>
        </div>
      </div>

      <!-- 筛选条 + 卡片列表 -->
      <div class="bg-card rounded-lg shadow-sm">
        <div class="flex flex-wrap items-center gap-3 p-4">
          <InputSearch
            v-model:value="query.keyword"
            :placeholder="$t('device.name') + ' / ' + $t('device.deviceKey')"
            class="max-w-64"
            allow-clear
            @search="onSearch"
          />
          <Select
            v-model:value="query.status"
            :options="[
              { label: $t('device.statusActive'), value: '1' },
              { label: $t('device.statusOffline'), value: '2' },
              { label: $t('device.statusInactive'), value: '0' },
            ]"
            :placeholder="$t('device.status')"
            allow-clear
            class="w-32"
            @change="onFilterChange"
          />
          <Select
            v-model:value="query.productId"
            :options="productOptions"
            :placeholder="$t('device.product')"
            allow-clear
            class="w-52"
            show-search
            option-filter-prop="label"
            @change="onFilterChange"
          />
          <div class="flex-1"></div>
          <Button class="ml-2" @click="onExportSelected">
            {{ $t('product.exportSelected') }}
          </Button>
          <Button class="ml-2" @click="onExportAll">
            {{ $t('product.exportAll') }}
          </Button>
          <Button class="ml-2" @click="importOpen = true">
            {{ $t('product.import') }}
          </Button>
          <Button type="primary" @click="onCreate">
            <Plus class="size-5" />
            {{ $t('ui.actionTitle.create', [$t('page.device.list')]) }}
          </Button>
        </div>

        <div class="px-4 pb-4">
          <div
            v-if="items.length === 0 && !loading"
            class="text-muted-foreground py-10 text-center text-sm"
          >
            {{ $t('product.thingModel.emptySection') }}
          </div>
          <div
            v-else
            :class="{ 'pointer-events-none opacity-50': loading }"
            class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
          >
            <div
              v-for="row in items"
              :key="row.id"
              :class="selectedIds.includes(row.id) ? 'border-primary shadow-primary/10 ring-primary/30 ring-2' : ''"
              class="hover:border-primary/40 relative flex cursor-pointer flex-col rounded-lg border p-4 transition-all hover:shadow-md"
              @click="toggleSelect(row.id)"
            >
              <span
                :class="selectedIds.includes(row.id) ? 'bg-primary border-primary' : 'border-border bg-card'"
                class="absolute top-3 left-3 z-10 flex size-5 items-center justify-center rounded-full border transition-colors"
                @click.stop="toggleSelect(row.id)"
              >
                <span v-if="selectedIds.includes(row.id)" class="icon-[lucide--check] text-primary-foreground size-3"></span>
              </span>

              <div class="flex items-center justify-between gap-2 pl-7">
                <div class="text-foreground truncate font-medium">{{ row.name }}</div>
                <Tag :color="STATUS_META[row.status]?.color" class="!m-0 shrink-0">
                  {{ STATUS_META[row.status]?.label }}
                </Tag>
              </div>
              <div class="text-muted-foreground mt-1 font-mono text-xs">{{ row.deviceKey }}</div>

              <div class="text-muted-foreground mt-3 space-y-1.5 text-xs">
                <div class="flex items-center gap-1.5">
                  <span class="icon-[lucide--package] size-3.5 shrink-0"></span>
                  <span class="truncate">{{ row.productName }}</span>
                </div>
                <div class="flex items-center gap-1.5">
                  <span class="icon-[lucide--cpu] size-3.5 shrink-0"></span>
                  <span>{{ row.firmware }}</span>
                </div>
                <div class="flex items-center gap-1.5">
                  <span class="icon-[lucide--clock] size-3.5 shrink-0"></span>
                  <span>{{ row.lastOnline }}</span>
                </div>
                <div class="flex items-center gap-1.5">
                  <span class="icon-[lucide--map-pin] size-3.5 shrink-0"></span>
                  <span class="truncate">{{ row.remark }}</span>
                </div>
              </div>

              <div class="border-border/60 mt-4 flex items-center gap-1 border-t pt-3" @click.stop>
                <Button type="link" size="small" @click="onEdit(row)">
                  {{ $t('common.edit') }}
                </Button>
                <Button danger size="small" type="text" @click="onDelete(row)">
                  {{ $t('common.delete') }}
                </Button>
              </div>
            </div>
          </div>

          <div class="mt-4 flex justify-end pt-2">
            <Pagination
              v-model:current="query.page"
              v-model:pageSize="query.pageSize"
              :total="total"
              :page-size-options="['12', '24', '36']"
              show-size-changer
              :show-total="(t: number) => $t('product.thingModel.totalItems', [t])"
              @change="onPageChange"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑 -->
    <Modal v-model:open="formOpen" :title="formTitle" destroy-on-close>
      <div class="space-y-3 py-2">
        <div>
          <div class="mb-1 text-sm">{{ $t('device.name') }} *</div>
          <Input v-model:value="formData.name" :placeholder="$t('device.name')" />
        </div>
        <div>
          <div class="mb-1 text-sm">{{ $t('device.product') }} *</div>
          <Select
            v-model:value="formData.productId"
            :options="productOptions"
            class="w-full"
            show-search
            option-filter-prop="label"
            :placeholder="$t('device.product')"
          />
        </div>
        <div>
          <div class="mb-1 text-sm">{{ $t('device.deviceKey') }}</div>
          <Input
            v-model:value="formData.deviceKey"
            :disabled="formMode === 'edit'"
            :placeholder="$t('device.deviceKeyPlaceholder')"
          />
        </div>
        <div>
          <div class="mb-1 text-sm">{{ $t('device.remark') }}</div>
          <Input v-model:value="formData.remark" :placeholder="$t('device.remark')" />
        </div>
      </div>
      <template #footer>
        <Button @click="formOpen = false">{{ $t('common.cancel') }}</Button>
        <Button type="primary" @click="onSave">{{ $t('common.confirm') }}</Button>
      </template>
    </Modal>

    <!-- 导入 -->
    <Modal v-model:open="importOpen" :title="$t('product.importTitle')" destroy-on-close>
      <Upload.Dragger accept=".json,application/json" :max-count="1" :before-upload="handleImportFile">
        <p class="ant-upload-drag-icon">
          <span class="icon-[lucide--file-json] size-8"></span>
        </p>
        <p class="ant-upload-text">{{ $t('product.importFile') }}</p>
      </Upload.Dragger>
      <div v-if="importSummary" class="text-primary mt-3 text-sm">{{ importSummary }}</div>
      <div v-if="importError" class="text-destructive mt-3 text-sm">{{ importError }}</div>
      <template #footer>
        <Button @click="importOpen = false">{{ $t('common.cancel') }}</Button>
        <Button type="primary" @click="onImportConfirm">{{ $t('common.confirm') }}</Button>
      </template>
    </Modal>
  </Page>
</template>
