<script lang="ts" setup>
import type { ProductApi } from '#/api/product/product';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { useVbenForm } from '#/adapter/form';
import { createProduct, updateProduct } from '#/api/product/product';
import { $t } from '#/locales';

import { useFormSchema } from './data';

const emits = defineEmits(['success']);

const formData = ref<ProductApi.Product>();

const [Form, formApi] = useVbenForm({
  schema: useFormSchema(),
  showDefaultActions: false,
});

const id = ref();
const [Drawer, drawerApi] = useVbenDrawer<null | Partial<ProductApi.Product>>({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    // 来源不在表单字段内，由打开方通过 drawer data 注入
    values.source = drawerApi.getData()?.source ?? 'custom';
    // 创建时若未指定 ProductKey，则自动生成（mock 语义，后端会落库真实唯一键）
    if (!id.value && !values.productKey) {
      values.productKey = crypto.randomUUID().replaceAll('-', '').slice(0, 11);
    }
    drawerApi.lock();
    (id.value ? updateProduct(id.value, values) : createProduct(values))
      .then(() => {
        emits('success');
        drawerApi.close();
      })
      .catch(() => {
        drawerApi.unlock();
      });
  },

  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = drawerApi.getData();
      formApi.reset();

      if (data?.id) {
        // 走到此分支时必为完整记录（编辑态），创建态仅携带 source
        formData.value = data as ProductApi.Product;
        id.value = data.id;
        formApi.setValues(data);
      } else {
        formData.value = undefined;
        id.value = undefined;
      }
    }
  },
});

defineExpose({ drawerApi });

const getDrawerTitle = computed(() => {
  return formData.value?.id
    ? $t('common.edit', $t('product.title'))
    : $t('common.create', $t('product.title'));
});
</script>
<template>
  <Drawer :title="getDrawerTitle">
    <Form />
  </Drawer>
</template>
