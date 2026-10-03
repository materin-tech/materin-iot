<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message, Upload } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createThingModel } from '#/api/product/thing-model';
import { $t } from '#/locales';
import { readTextFile } from '#/utils/file-download';

import { loadProductOptions } from '../data';

defineOptions({ name: 'ThingModelImportDsl' });

const emits = defineEmits<{ success: [] }>();

/** 解析出的 DSL model 列表（兼容单个/批量/裸 model） */
const parsed = ref<any[]>([]);
const parseError = ref('');

/** DSL 里的产品名仅供参考，导入时需指定目标产品 */
const [Form, formApi] = useVbenForm({
  layout: 'vertical',
  schema: [
    {
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: loadProductOptions,
        class: 'w-full',
        showSearch: true,
        optionFilterProp: 'label',
      },
      fieldName: 'productId',
      label: $t('product.thingModel.importTargetProduct'),
      rules: 'required',
    },
  ],
  showDefaultActions: false,
});

function reset() {
  parsed.value = [];
  parseError.value = '';
  formApi.reset();
}

/** 判断一个对象是否为合法的 model 结构 */
function isModel(value: any): boolean {
  return (
    value &&
    ['properties', 'methods', 'events'].every((key) =>
      Array.isArray(value[key]),
    )
  );
}

/** 解析 DSL JSON：兼容 {models:[...]} 批量 / {model:{...}} 单个 / 裸 model */
async function handleBeforeUpload(file: File) {
  parsed.value = [];
  parseError.value = '';
  try {
    const text = await readTextFile(file);
    const data = JSON.parse(text);
    let models: any[] = [];
    if (Array.isArray(data?.models)) {
      models = data.models;
    } else if (data?.model) {
      models = [data.model];
    } else if (isModel(data)) {
      models = [data];
    }
    const valid = models.filter(isModel);
    if (valid.length === 0) {
      parseError.value = $t('product.thingModel.dslInvalid');
    } else {
      parsed.value = valid;
    }
  } catch {
    parseError.value = $t('product.importInvalid');
  }
  return false;
}

const parseSummary = computed(() => {
  if (parsed.value.length === 0) return '';
  return $t('product.thingModel.dslBatchParsed', [parsed.value.length]);
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    if (parsed.value.length === 0) {
      parseError.value = $t('product.thingModel.dslInvalid');
      return;
    }
    modalApi.lock();
    try {
      const { productId: fallbackProductId } = await formApi.getValues();
      const productOptions = await loadProductOptions();
      let imported = 0;
      let failed = 0;
      for (const model of parsed.value) {
        // 优先按 DSL 里的产品名匹配归属，匹配不到则用弹窗指定的目标产品
        const matched = productOptions.find(
          (option) => option.label === model.productName,
        );
        const productId = matched?.value ?? fallbackProductId;
        if (!productId) {
          failed++;
          continue;
        }
        try {
          await createThingModel({
            productId,
            productName: matched?.label ?? productOptions.find((option) => option.value === fallbackProductId)?.label ?? '',
            version: model.version || 'v1.0',
            status: 0,
            remark: model.remark,
            properties: model.properties,
            methods: model.methods,
            events: model.events,
          });
          imported++;
        } catch {
          failed++;
        }
      }
      message.success($t('product.importResult', [imported, failed]));
      emits('success');
      modalApi.close();
    } finally {
      modalApi.lock(false);
    }
  },
  onOpenChange(isOpen) {
    if (!isOpen) reset();
  },
});

defineExpose({ modalApi });
</script>
<template>
  <Modal class="w-[600px]" :title="$t('product.thingModel.importDslTitle')">
    <Form />
    <Upload.Dragger
      accept=".json,application/json"
      :max-count="1"
      :before-upload="handleBeforeUpload"
    >
      <p class="ant-upload-drag-icon">
        <span class="icon-[lucide--file-json] size-8"></span>
      </p>
      <p class="ant-upload-text">{{ $t('product.thingModel.dslFile') }}</p>
    </Upload.Dragger>

    <div v-if="parseSummary" class="text-primary mt-3 text-sm">
      {{ parseSummary }}
    </div>
    <div v-if="parseError" class="text-destructive mt-3 text-sm">
      {{ parseError }}
    </div>
  </Modal>
</template>
