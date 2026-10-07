<script lang="ts" setup>
import type { ThingModelApi } from '#/api/product/thing-model';

import { onMounted, reactive, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, Empty, Input, message, Pagination, Popconfirm, Tag } from 'ant-design-vue';

import {
  deleteThingModel,
  getThingModelList,
} from '#/api/product/thing-model';
import { $t } from '#/locales';
import { downloadJsonFile } from '#/utils/file-download';

import { THING_MODEL_SECTIONS } from './data';
import Form from './modules/form.vue';
import ImportDsl from './modules/import-dsl.vue';

defineOptions({ name: 'ProductThingModel' });

const InputSearch = Input.Search;

const items = ref<ThingModelApi.ThingModel[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({ keyword: '', page: 1, pageSize: 12 });
/** 勾选的模型 ID（批量导出用） */
const selectedIds = ref<string[]>([]);

/** 统计条：模型总数 / 草稿 / 已发布 */
const stats = ref({ draft: 0, published: 0, total: 0 });

async function fetchList() {
  loading.value = true;
  try {
    const res = await getThingModelList({
      keyword: query.keyword || undefined,
      page: query.page,
      pageSize: query.pageSize,
    });
    items.value = res.items;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

async function loadStats() {
  const [all, draft, published] = await Promise.all([
    getThingModelList({ page: 1, pageSize: 1 }),
    getThingModelList({ page: 1, pageSize: 1, status: 0 }),
    getThingModelList({ page: 1, pageSize: 1, status: 1 }),
  ]);
  stats.value = {
    draft: draft.total,
    published: published.total,
    total: all.total,
  };
}

async function refresh() {
  await Promise.all([fetchList(), loadStats()]);
}

const statTiles = [
  {
    icon: 'icon-[lucide--boxes]',
    key: 'total',
    label: $t('product.thingModel.total'),
    valueClass: 'text-primary',
  },
  {
    icon: 'icon-[lucide--circle-dashed]',
    key: 'draft',
    label: $t('product.thingModel.statusDraft'),
    valueClass: 'text-amber-500',
  },
  {
    icon: 'icon-[lucide--circle-check]',
    key: 'published',
    label: $t('product.thingModel.statusPublished'),
    valueClass: 'text-emerald-500',
  },
];

function onSearch(keyword: string) {
  query.keyword = keyword;
  query.page = 1;
  fetchList();
}

function onPageChange(page: number, pageSize: number) {
  query.page = page;
  query.pageSize = pageSize;
  fetchList();
}

function onCreate() {
  formModalApi.setData(null).open();
}

/** 条目 → DSL model 结构 */
function toDsl(row: ThingModelApi.ThingModel) {
  return {
    productName: row.productName,
    version: row.version,
    remark: row.remark,
    properties: row.properties,
    methods: row.methods,
    events: row.events,
  };
}

/** 导出单个物模型 DSL（JSON 信封格式） */
function onExportDsl(row: ThingModelApi.ThingModel) {
  downloadJsonFile(
    {
      spec: 'materin-thing-model',
      specVersion: '1.0',
      exportedAt: new Date().toISOString(),
      model: toDsl(row),
    },
    `thing-model-${row.productName}-${row.version}.json`.replaceAll(' ', '_'),
  );
}

/** 批量导出物模型 DSL（models 数组信封） */
function exportModels(rows: ThingModelApi.ThingModel[], suffix = '') {
  downloadJsonFile(
    {
      spec: 'materin-thing-model',
      specVersion: '1.0',
      exportedAt: new Date().toISOString(),
      models: rows.map((row) => toDsl(row)),
    },
    `materin-thing-models${suffix}-${new Date().toISOString().slice(0, 10)}.json`,
  );
}

/** 选择导出：仅导出勾选卡片 */
function onExportSelected() {
  const rows = items.value.filter((row) => selectedIds.value.includes(row.id));
  if (rows.length === 0) {
    message.warning($t('product.exportSelectedEmpty'));
    return;
  }
  exportModels(rows, '-selected');
}

/** 全部导出：全量物模型 */
async function onExportAll() {
  const res = await getThingModelList({ page: 1, pageSize: 200 });
  exportModels(res.items);
}

/** 卡片勾选 */
function toggleSelect(id: string) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id];
}

function onEdit(row: ThingModelApi.ThingModel) {
  formModalApi.setData(row).open();
}

function onDelete(row: ThingModelApi.ThingModel) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.productName]),
    duration: 0,
    key: 'action_process_msg',
  });
  deleteThingModel(row.id)
    .then(() => {
      message.success({
        content: $t('ui.actionMessage.deleteSuccess', [row.productName]),
        key: 'action_process_msg',
      });
      refresh();
    })
    .catch(() => {
      hideLoading();
    });
}

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [ImportDslModal, importDslModalApi] = useVbenModal({
  connectedComponent: ImportDsl,
  destroyOnClose: true,
});

onMounted(() => {
  refresh();
});
</script>

<template>
  <Page :title="$t('page.product.thingModel')">
    <FormModal @success="refresh" />
    <ImportDslModal @success="refresh" />
    <div class="flex flex-col gap-4">
      <!-- 顶部：模型与三要素统计 -->
      <div class="grid grid-cols-3 gap-4">
        <div
          v-for="tile in statTiles"
          :key="tile.key"
          class="bg-card hover:border-primary/40 flex items-center gap-4 rounded-lg border border-transparent p-5 shadow-sm transition-all hover:shadow-md"
        >
          <div
            class="bg-primary/10 flex size-12 shrink-0 items-center justify-center rounded-lg"
          >
            <span :class="[tile.icon, tile.valueClass]" class="size-6"></span>
          </div>
          <div class="min-w-0">
            <div class="text-muted-foreground text-xs">{{ tile.label }}</div>
            <div
              class="text-foreground mt-1 text-2xl leading-none font-semibold tabular-nums"
            >
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
            :placeholder="
              $t('product.thingModel.productName') +
              ' / ' +
              $t('product.thingModel.version')
            "
            class="max-w-64"
            allow-clear
            @search="onSearch"
          />
          <div class="flex-1"></div>
          <Button class="ml-2" @click="onExportSelected">
            {{ $t('product.exportSelected') }}
          </Button>
          <Button class="ml-2" @click="onExportAll">
            {{ $t('product.exportAll') }}
          </Button>
          <Button class="ml-2" @click="importDslModalApi.open()">
            {{ $t('product.thingModel.importDsl') }}
          </Button>
          <Button type="primary" @click="onCreate">
            <Plus class="size-5" />
            {{ $t('ui.actionTitle.create', [$t('page.product.thingModel')]) }}
          </Button>
        </div>

        <div class="px-4 pb-4">
          <Empty v-if="!loading && items.length === 0" />
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
              <!-- 勾选圈 -->
              <span
                :class="selectedIds.includes(row.id) ? 'bg-primary border-primary' : 'border-border bg-card'"
                class="absolute top-3 left-3 z-10 flex size-5 items-center justify-center rounded-full border transition-colors"
                @click.stop="toggleSelect(row.id)"
              >
                <span
                  v-if="selectedIds.includes(row.id)"
                  class="icon-[lucide--check] text-primary-foreground size-3"
                ></span>
              </span>

              <!-- 卡头：产品 + 版本 -->
              <div class="flex items-center justify-between gap-2 pl-7">
                <div class="text-foreground truncate font-medium">
                  {{ row.productName }}
                </div>
                <Tag color="geekblue" class="!m-0 shrink-0">
                  {{ row.version }}
                </Tag>
              </div>

              <!-- 三要素计数 -->
              <div class="mt-3 grid grid-cols-3 gap-2">
                <div
                  v-for="section in THING_MODEL_SECTIONS"
                  :key="section.itemsKey"
                  class="bg-accent/50 flex flex-col items-center rounded-md py-2"
                >
                  <span
                    :class="section.icon"
                    class="text-muted-foreground size-4"
                  ></span>
                  <span class="text-foreground mt-1 text-base font-semibold tabular-nums">
                    {{ row[section.itemsKey]?.length ?? 0 }}
                  </span>
                  <span class="text-muted-foreground text-xs">
                    {{ section.label }}
                  </span>
                </div>
              </div>

              <!-- 元信息 -->
              <div class="text-muted-foreground mt-3 space-y-1.5 text-xs">
                <div class="flex items-center gap-1.5">
                  <span
                    :class="
                      row.status === 1
                        ? 'icon-[lucide--circle-check] text-emerald-500'
                        : 'icon-[lucide--circle-dashed] text-amber-500'
                    "
                    class="size-3.5 shrink-0"
                  ></span>
                  <span>
                    {{
                      row.status === 1
                        ? $t('product.thingModel.statusPublished')
                        : $t('product.thingModel.statusDraft')
                    }}
                  </span>
                </div>
                <div class="flex items-center gap-1.5">
                  <span class="icon-[lucide--clock] size-3.5 shrink-0"></span>
                  <span>{{ row.createTime }}</span>
                </div>
              </div>

              <!-- 卡脚：操作 -->
              <div
                class="border-border/60 mt-4 flex items-center gap-1 border-t pt-3"
              >
                <Button type="link" size="small" @click="onExportDsl(row)">
                  {{ $t('product.thingModel.exportDsl') }}
                </Button>
                <Button type="link" size="small" @click="onEdit(row)">
                  {{ $t('common.edit') }}
                </Button>
                <Popconfirm
                  :title="$t('ui.actionMessage.deleteConfirm', [row.productName])"
                  @confirm="onDelete(row)"
                >
                  <Button type="link" size="small" danger>
                    {{ $t('common.delete') }}
                  </Button>
                </Popconfirm>
              </div>
            </div>
          </div>

          <!-- 分页 -->
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
  </Page>
</template>
