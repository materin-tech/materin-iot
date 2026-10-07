<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ProductApi } from '#/api/product/product';

import { onMounted, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message } from 'ant-design-vue';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { deleteProduct, getProductList } from '#/api/product/product';
import { $t } from '#/locales';
import { createDateRangeCodec } from '#/utils/date-range-codec';
import { downloadJsonFile } from '#/utils/file-download';

import { useColumns, useGridFormSchema } from './data';
import Form from './form.vue';
import ImportModal from './import-modal.vue';

const props = defineProps<{
  /** 产品来源：standard-标准产品 custom-自定义产品 */
  source: ProductApi.ProductSource;
}>();

interface SearchFormValues extends Record<string, unknown> {
  createTime?: [Dayjs, Dayjs];
}

const searchCodec = createDateRangeCodec<SearchFormValues>()({
  endField: 'endTime',
  rangeField: 'createTime',
  startField: 'startTime',
});

type SearchSubmitValues = ReturnType<typeof searchCodec.encode>;

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    codec: searchCodec,
    schema: useGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues: SearchSubmitValues) => {
          return await getProductList({
            page: page.currentPage,
            pageSize: page.pageSize,
            source: props.source,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      search: true,
      zoom: true,
    },
  } as VxeTableGridOptions<ProductApi.Product>,
});

// 统计条数据（基于当前来源的未过滤总量）
const stats = ref({ draft: 0, published: 0, total: 0 });

async function loadStats() {
  const [all, published, draft] = await Promise.all([
    getProductList({ page: 1, pageSize: 1, source: props.source }),
    getProductList({ page: 1, pageSize: 1, source: props.source, status: 1 }),
    getProductList({ page: 1, pageSize: 1, source: props.source, status: 0 }),
  ]);
  stats.value = {
    draft: draft.total,
    published: published.total,
    total: all.total,
  };
}

const statTiles = [
  {
    icon: 'icon-[lucide--package]',
    key: 'total',
    label: $t('product.totalProducts'),
    valueClass: 'text-primary',
  },
  {
    icon: 'icon-[lucide--circle-check]',
    key: 'published',
    label: $t('product.publishedCount'),
    valueClass: 'text-emerald-500',
  },
  {
    icon: 'icon-[lucide--flask-conical]',
    key: 'draft',
    label: $t('product.draftCount'),
    valueClass: 'text-amber-500',
  },
];

function onCreate() {
  formDrawerApi.setData({ source: props.source }).open();
}

const importOpen = ref(false);

/** 导出 JSON 文件（产品列表） */
function exportRows(rows: ProductApi.Product[], suffix = '') {
  const date = new Date().toISOString().slice(0, 10);
  downloadJsonFile(
    {
      type: 'materin-products',
      source: props.source,
      exportedAt: new Date().toISOString(),
      items: rows,
    },
    `materin-products-${props.source}${suffix}-${date}.json`,
  );
}

/** 全部导出：当前来源全部产品 */
async function onExportAll() {
  const res = await getProductList({
    page: 1,
    pageSize: 1000,
    source: props.source,
  });
  exportRows(res.items);
}

/** 选择导出：仅导出勾选行 */
function onExportSelected() {
  const rows = (gridApi.grid?.getCheckboxRecords?.() ??
    []) as ProductApi.Product[];
  if (rows.length === 0) {
    message.warning($t('product.exportSelectedEmpty'));
    return;
  }
  exportRows(rows, '-selected');
}

function onEdit(row: ProductApi.Product) {
  formDrawerApi.setData({ ...row, source: props.source }).open();
}

function onDelete(row: ProductApi.Product) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
    key: 'action_process_msg',
  });
  deleteProduct(row.id)
    .then(() => {
      message.success({
        content: $t('ui.actionMessage.deleteSuccess', [row.name]),
        key: 'action_process_msg',
      });
      onRefresh();
    })
    .catch(() => {
      hideLoading();
    });
}

function onRefresh() {
  gridApi.query();
  loadStats();
}

onMounted(() => {
  loadStats();
});
</script>

<template>
  <Page auto-content-height :title="$t(`page.product.${props.source}`)">
    <FormDrawer @success="onRefresh" />
    <ImportModal v-model:open="importOpen" :source="props.source" @success="onRefresh" />
    <div class="flex h-full flex-col gap-4">
      <!-- 顶部：产品数量统计 -->
      <div class="grid shrink-0 grid-cols-3 gap-4">
        <div
          v-for="tile in statTiles"
          :key="tile.key"
          class="bg-card hover:border-primary/40 hover:shadow-primary/5 flex items-center gap-4 rounded-lg border border-transparent p-5 shadow-sm transition-all hover:shadow-md"
        >
          <div
            class="bg-primary/10 flex size-12 shrink-0 items-center justify-center rounded-lg"
          >
            <span
              :class="[tile.icon, tile.valueClass]"
              class="size-6"
            ></span>
          </div>
          <div class="min-w-0">
            <div class="text-muted-foreground text-xs">
              {{ tile.label }}
            </div>
            <div
              class="text-foreground mt-1 text-2xl leading-none font-semibold tabular-nums"
            >
              {{ stats[tile.key as 'draft' | 'published' | 'total'] }}
            </div>
          </div>
        </div>
      </div>

      <!-- 下方：产品分页表格 -->
      <div class="min-h-0 flex-1">
        <Grid :table-title="$t('product.list')">
          <template #toolbar-tools>
            <Button class="ml-2" @click="importOpen = true">
              {{ $t('product.import') }}
            </Button>
            <Button class="ml-2" @click="onExportSelected">
              {{ $t('product.exportSelected') }}
            </Button>
            <Button class="ml-2" @click="onExportAll">
              {{ $t('product.exportAll') }}
            </Button>
            <Button class="ml-2" type="primary" @click="onCreate">
              <Plus class="size-5" />
              {{ $t('ui.actionTitle.create', [$t('product.title')]) }}
            </Button>
          </template>
          <template #action="{ row }">
            <VbenTableAction
              :actions="[
                {
                  text: $t('common.edit'),
                  icon: 'lucide:edit',
                  onClick: () => onEdit(row),
                },
              ]"
              :dropdown-actions="[
                {
                  text: $t('common.delete'),
                  icon: 'lucide:trash-2',
                  danger: true,
                  popConfirm: {
                    title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                    confirm: () => onDelete(row),
                  },
                },
              ]"
              align="center"
            />
          </template>
        </Grid>
      </div>
    </div>
  </Page>
</template>
