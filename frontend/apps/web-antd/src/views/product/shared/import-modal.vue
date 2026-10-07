<script lang="ts" setup>
import type { ProductApi } from '#/api/product/product';

import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message, Upload } from 'ant-design-vue';

import { importProducts } from '#/api/product/product';
import { $t } from '#/locales';
import { readTextFile } from '#/utils/file-download';

defineOptions({ name: 'ProductImportModal' });

const props = defineProps<{
  source: ProductApi.ProductSource;
}>();

const open = defineModel<boolean>('open', { default: false });

const emits = defineEmits<{
  success: [];
}>();

/** 解析出的待导入条目 */
const parsed = ref<any[]>([]);
const fileName = ref('');
const parseError = ref('');

const parsedCount = computed(() => parsed.value.length);

function reset() {
  parsed.value = [];
  fileName.value = '';
  parseError.value = '';
}

/** 手动接管上传，读取文件内容并解析 JSON */
async function handleBeforeUpload(file: File) {
  reset();
  fileName.value = file.name;
  try {
    const text = await readTextFile(file);
    const data = JSON.parse(text);
    const items = Array.isArray(data) ? data : (data.items ?? []);
    parsed.value = items.filter((item: any) => item?.name);
    if (parsed.value.length === 0) {
      parseError.value = $t('product.importEmpty');
    } else {
      parseError.value = '';
    }
  } catch {
    parseError.value = $t('product.importInvalid');
  }
  // 返回 false 阻止 antd 自动上传
  return false;
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (parsed.value.length === 0) {
      parseError.value = $t('product.importEmpty');
      return;
    }
    modalApi.lock();
    try {
      const res = await importProducts({
        // 条目自带 source 时以条目为准（后端兜底），这里显式传当前页来源
        items: parsed.value.map((item) => ({ ...item, source: props.source })),
        source: props.source,
      });
      message.success($t('product.importResult', [res.imported, res.failed]));
      emits('success');
      open.value = false;
    } finally {
      modalApi.lock(false);
    }
  },
  onOpenChange(isOpen) {
    if (!isOpen) {
      reset();
    }
  },
});

/** 手动渲染模式下，把 v-model:open 同步到 modalApi */
watch(
  open,
  (val) => {
    if (val) {
      modalApi.open();
    } else {
      modalApi.close();
    }
  },
  { immediate: true },
);

defineExpose({ modalApi });
</script>
<template>
  <Modal class="w-[560px]" :title="$t('product.importTitle')">
    <Upload.Dragger
      accept=".json,application/json"
      :max-count="1"
      :before-upload="handleBeforeUpload"
    >
      <p class="ant-upload-drag-icon">
        <span class="icon-[lucide--file-json] size-8"></span>
      </p>
      <p class="ant-upload-text">{{ $t('product.importFile') }}</p>
    </Upload.Dragger>

    <div v-if="fileName" class="mt-3 text-sm">
      <span class="text-muted-foreground">{{ fileName }}</span>
    </div>
    <div v-if="parsedCount > 0" class="text-primary mt-2 text-sm">
      {{ $t('product.importParsed', [parsedCount]) }}
    </div>
    <div v-if="parseError" class="text-destructive mt-2 text-sm">
      {{ parseError }}
    </div>
  </Modal>
</template>
