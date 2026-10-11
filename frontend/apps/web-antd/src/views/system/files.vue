<!--
MIT License

Copyright (c) 2026 materin-tech

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
-->

<script setup lang="ts">
import type { FileApi } from '#/api/system/file';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Input, message, Modal, Pagination, Select } from 'ant-design-vue';

import { deleteFile, getFileDownloadUrl, getFiles } from '#/api';

defineOptions({ name: 'SystemFiles' });

const BIZ_TYPE_OPTIONS = [
  { label: '全部来源', value: '' },
  { label: '固件升级（OTA）', value: 'ota' },
  { label: '其他 / 通用', value: 'generic' },
];

const rows = ref<FileApi.SysFile[]>([]);
const total = ref(0);
const query = reactive({ bizType: '', keyword: '', page: 1, pageSize: 10 });

function formatSize(bytes?: number) {
  if (!bytes && bytes !== 0) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

async function load() {
  const data = (await getFiles({
    page: query.page,
    pageSize: query.pageSize,
    ...(query.bizType ? { bizType: query.bizType } : {}),
    ...(query.keyword ? { keyword: query.keyword } : {}),
  })) as any;
  rows.value = data?.items ?? [];
  total.value = data?.total ?? 0;
}

function search() {
  query.page = 1;
  load();
}

async function download(row: FileApi.SysFile) {
  const res = (await getFileDownloadUrl(row.id!)) as any;
  if (res?.url) {
    window.open(res.url, '_blank');
  } else {
    message.error('获取下载地址失败');
  }
}

function remove(row: FileApi.SysFile) {
  const referenced = row.bizType && row.bizType !== 'generic';
  Modal.confirm({
    title: '删除文件',
    content: referenced
      ? `该文件来自「${row.bizType}」业务，删除后相关业务将无法下载该文件，且不可恢复。确认删除？`
      : '文件将从存储中删除且不可恢复，确认？',
    okButtonProps: { danger: true },
    onOk: async () => {
      await deleteFile(row.id!);
      message.success('已删除');
      await load();
    },
  });
}

onMounted(load);
</script>

<template>
  <Page :title="$t('system.files.title')">
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <Select
        v-model:value="query.bizType"
        :options="BIZ_TYPE_OPTIONS"
        class="w-44"
        @change="search"
      />
      <Input
        v-model:value="query.keyword"
        allow-clear
        class="w-64"
        placeholder="搜索文件名 / 存储路径"
        @press-enter="search"
      />
      <Button @click="search">搜索</Button>
    </div>

    <div class="rounded-lg border">
      <table class="w-full text-sm">
        <thead class="bg-accent text-left">
          <tr>
            <th class="px-4 py-2">文件名</th>
            <th class="px-4 py-2">业务来源</th>
            <th class="px-4 py-2">大小</th>
            <th class="px-4 py-2">类型</th>
            <th class="px-4 py-2">存储位置</th>
            <th class="px-4 py-2">上传时间</th>
            <th class="px-4 py-2">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="f in rows" :key="f.id" class="border-t">
            <td class="max-w-48 truncate px-4 py-2 font-medium" :title="f.fileName">
              {{ f.fileName }}
            </td>
            <td class="px-4 py-2">
              <span
                class="rounded px-1.5 py-0.5 text-xs"
                :class="f.bizType === 'generic'
                  ? 'bg-accent text-accent-foreground'
                  : 'bg-primary/10 text-primary'"
              >
                {{ f.bizType === 'ota' ? '固件升级' : f.bizType === 'generic' ? '通用' : f.bizType }}
              </span>
              <span v-if="f.bizRefId" class="text-muted-foreground ml-1 text-xs">#{{ f.bizRefId }}</span>
            </td>
            <td class="px-4 py-2 tabular-nums">{{ formatSize(f.size) }}</td>
            <td class="px-4 py-2 text-muted-foreground max-w-32 truncate" :title="f.contentType">
              {{ f.contentType || '-' }}
            </td>
            <td class="px-4 py-2">
              <div class="max-w-64 truncate font-mono text-xs" :title="`bucket: ${f.bucket}`">
                {{ f.storageKey }}
              </div>
              <div class="text-muted-foreground text-xs">bucket: {{ f.bucket }}</div>
            </td>
            <td class="px-4 py-2 whitespace-nowrap">{{ (f.createTime || '').replace('T', ' ') }}</td>
            <td class="px-4 py-2">
              <Button class="mr-2" size="small" @click="download(f)">下载</Button>
              <Button danger size="small" @click="remove(f)">删除</Button>
            </td>
          </tr>
          <tr v-if="rows.length === 0">
            <td class="text-muted-foreground px-4 py-8 text-center" colspan="7">
              暂无文件（通过业务模块上传的文件会自动登记到这里）
            </td>
          </tr>
          <tr v-if="rows.length === 0"></tr>
        </tbody>
      </table>
    </div>

    <div class="mt-4 flex justify-end">
      <Pagination
        v-model:current="query.page"
        v-model:page-size="query.pageSize"
        :show-size-changer="true"
        :show-total="(t: number) => `共 ${t} 个文件`"
        :total="total"
        @change="load"
      />
    </div>
  </Page>
</template>
