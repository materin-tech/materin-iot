<script setup lang="ts">
import type { OtaApi } from '#/api/ota/ota';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button, Input, message, Modal, Select, Upload,
} from 'ant-design-vue';

import { deleteOtaPackage, getOtaPackages, uploadOtaPackage } from '#/api';
import { getProductList } from '#/api';

defineOptions({ name: 'OtaPackages' });

const rows = ref<OtaApi.OtaPackage[]>([]);
const total = ref(0);
const query = reactive({ page: 1, pageSize: 20 });
const products = ref<any[]>([]);
const addOpen = ref(false);
const uploading = ref(false);
const form = reactive({
  file: null as File | null,
  module: '',
  name: '',
  productId: undefined as number | undefined,
  version: '',
});

async function load() {
  const data = (await getOtaPackages(query)) as any;
  rows.value = data?.items ?? [];
  total.value = data?.total ?? 0;
}

async function submit() {
  if (!form.file || !form.productId || !form.name || !form.version) {
    message.warning('请完整填写固件名/产品/版本并选择文件');
    return;
  }
  const product = products.value.find((p) => Number(p.id) === form.productId);
  const fd = new FormData();
  fd.append('file', form.file);
  fd.append('productId', String(form.productId));
  fd.append('productName', product?.name ?? '');
  fd.append('name', form.name);
  fd.append('version', form.version);
  fd.append('module', form.module);
  fd.append('signMethod', 'md5');
  uploading.value = true;
  try {
    await uploadOtaPackage(fd);
    message.success('上传成功（文件已存对象存储）');
    addOpen.value = false;
    await load();
  } finally {
    uploading.value = false;
  }
}

async function remove(id?: number) {
  if (!id) return;
  Modal.confirm({
    content: '将同时删除对象存储中的固件文件，确认？',
    onOk: async () => {
      await deleteOtaPackage(id);
      message.success('已删除');
      await load();
    },
  });
}

onMounted(async () => {
  await load();
  const data = await getProductList({ page: 1, pageSize: 200 });
  products.value = (data as any)?.items ?? [];
});
</script>

<template>
  <Page :title="$t('page.ota.packages.title')">
    <div class="mb-4 flex items-center gap-3">
      <Button type="primary" @click="addOpen = true">上传固件包</Button>
    </div>
    <div class="rounded-lg border">
      <table class="w-full text-sm">
        <thead class="bg-accent text-left">
          <tr>
            <th class="px-4 py-2">固件名</th>
            <th class="px-4 py-2">产品</th>
            <th class="px-4 py-2">版本</th>
            <th class="px-4 py-2">类型/模块</th>
            <th class="px-4 py-2">大小</th>
            <th class="px-4 py-2">MD5</th>
            <th class="px-4 py-2">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in rows" :key="p.id" class="border-t">
            <td class="px-4 py-2">{{ p.name }}</td>
            <td class="px-4 py-2">{{ p.productName || p.productId }}</td>
            <td class="px-4 py-2 tabular-nums">{{ p.version }}</td>
            <td class="px-4 py-2">{{ p.type }} / {{ p.module || '-' }}</td>
            <td class="px-4 py-2 tabular-nums">{{ p.size }} B</td>
            <td class="px-4 py-2 font-mono text-xs">{{ p.signValue }}</td>
            <td class="px-4 py-2">
              <Button danger size="small" @click="remove(p.id)">删除</Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal v-model:open="addOpen" title="上传固件包" :confirm-loading="uploading" @ok="submit">
      <div class="space-y-3">
        <Input v-model:value="form.name" placeholder="固件名称" />
        <Select v-model:value="form.productId" :options="products.map((p) => ({ label: p.name, value: Number(p.id) }))" class="w-full" placeholder="所属产品" show-search />
        <Input v-model:value="form.version" placeholder="目标版本（如 2.0.0）" />
        <Input v-model:value="form.module" placeholder="模块（可空，如 mcu）" />
        <Upload :before-upload="(f) => { form.file = f; return false; }" :max-count="1">
          <Button>选择固件文件</Button>
        </Upload>
        <div class="text-muted-foreground text-xs">
          文件经存储中间件（StorageClient）写入对象存储，平台仅保存元数据与校验值
        </div>
      </div>
    </Modal>
  </Page>
</template>
