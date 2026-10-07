<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import {
  Alert as AAlert,
  Button as AButton,
  Drawer as ADrawer,
  Pagination as APagination,
  Input as AInput,
  message,
  Select as ASelect,
  Table as ATable,
  Tag as ATag,
} from 'ant-design-vue';

import {
  getApiDetail,
  getApiList,
  getAppList,
  getAuthorizedApis,
  grantApis,
  revokeApis,
  saveApiAuth,
  syncApis,
} from '#/api/developer';

interface ApiItem {
  id: string;
  method: string;
  path: string;
  summary: string;
  tag: string;
}

const METHOD_COLORS: Record<string, string> = {
  GET: 'green', POST: 'blue', PUT: 'orange', DELETE: 'red', PATCH: 'purple',
};

const apps = ref<any[]>([]);
const appId = ref('');
const list = ref<ApiItem[]>([]);
const loading = ref(false);
const checkedIds = ref<string[]>([]);
const query = reactive({ page: 1, pageSize: 12, keyword: '', authFilter: 'all', total: 0 });
const detailOpen = ref(false);
const detail = ref<any>(null);
const detailLoading = ref(false);

async function loadApps() {
  const data: any = await getAppList({ page: 1, pageSize: 100 });
  apps.value = data.items;
  if (apps.value.length > 0) {
    appId.value = String(apps.value[0]!.id);
    await loadAuth();
  }
}

async function loadAuth() {
  if (!appId.value) return;
  checkedIds.value = await getAuthorizedApis(appId.value);
  query.page = 1;
  await loadApis();
}

async function loadApis() {
  loading.value = true;
  try {
    const params = {
      page: query.page,
      pageSize: query.pageSize,
      keyword: query.keyword,
      appId: appId.value || undefined,
      authFilter: query.authFilter === 'all' ? undefined : query.authFilter,
    };
    const data: any = await getApiList(params);
    list.value = data.items;
    query.total = data.total;
  } finally {
    loading.value = false;
  }
}

function onPageChange(page: number, pageSize: number) {
  query.page = page;
  query.pageSize = pageSize;
  loadApis();
}

function onFilterChange() {
  query.page = 1;
  loadApis();
}

function showTotal(total: number) {
  return `共 ${total} 个接口`;
}

async function doGrant() {
  if (!appId.value) {
    message.error('请先选择应用');
    return;
  }
  if (checkedIds.value.length === 0) {
    message.warning('请先勾选要授权的接口');
    return;
  }
  const added: any = await grantApis(appId.value, checkedIds.value);
  message.success(`已授权 ${added} 个接口（已授权的自动跳过）`);
  await loadAuth();
  await loadApis();
}

async function doRevoke() {
  if (!appId.value) {
    message.error('请先选择应用');
    return;
  }
  if (checkedIds.value.length === 0) {
    message.warning('请先勾选要取消的接口');
    return;
  }
  const removed: any = await revokeApis(appId.value, checkedIds.value);
  message.success(`已取消 ${removed} 个接口的授权`);
  await loadAuth();
  await loadApis();
}

async function doSync() {
  const added = await syncApis();
  message.success(`同步完成，新增 ${added} 个接口`);
  await loadApis();
}

async function save() {
  if (!appId.value) {
    message.error('请先选择应用');
    return;
  }
  await saveApiAuth(appId.value, checkedIds.value);
  message.success('授权已保存');
}

function toggleSelectPage(e: any) {
  const pageIds = list.value.map((i) => String(i.id));
  if (e.target.checked) {
    checkedIds.value = [...new Set([...checkedIds.value, ...pageIds])];
  } else {
    checkedIds.value = checkedIds.value.filter((x) => !pageIds.includes(x));
  }
}

async function selectAllFiltered() {
  const params = {
    page: 1,
    pageSize: 9999,
    keyword: query.keyword,
    appId: appId.value || undefined,
    authFilter: query.authFilter === 'all' ? undefined : query.authFilter,
  };
  const data: any = await getApiList(params);
  checkedIds.value = [...new Set([...checkedIds.value, ...data.items.map((i: any) => String(i.id))])];
  message.info(`已选中全部 ${data.total} 个筛选结果`);
}

function clearChecked() {
  checkedIds.value = [];
}

function toggleCheck(item: ApiItem) {
  const id = String(item.id);
  const idx = checkedIds.value.indexOf(id);
  if (idx >= 0) {
    checkedIds.value = checkedIds.value.filter((x) => x !== id);
  } else {
    checkedIds.value = [...checkedIds.value, id];
  }
}

async function openDetail(item: ApiItem) {
  detailOpen.value = true;
  detailLoading.value = true;
  try {
    detail.value = await getApiDetail(item.method, item.path);
  } finally {
    detailLoading.value = false;
  }
}

const paramColumns = [
  { title: '参数名', dataIndex: 'name', key: 'name', width: 130 },
  { title: '位置', dataIndex: 'in', key: 'in', width: 80 },
  { title: '类型', dataIndex: 'type', key: 'type', width: 90 },
  { title: '必填', key: 'required', width: 70 },
  { title: '说明', dataIndex: 'description', key: 'description' },
];
const fieldColumns = [
  { title: '字段', dataIndex: 'name', key: 'name', width: 150 },
  { title: '类型', dataIndex: 'type', key: 'type', width: 100 },
  { title: '说明', dataIndex: 'description', key: 'description' },
];

onMounted(async () => {
  await loadApps();
  await loadApis();
});
</script>

<template>
  <Page>
    <div class="bg-background p-4 rounded-lg">
      <div class="mb-3 flex items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <span>应用：</span>
          <ASelect
            v-model:value="appId"
            style="width: 220px"
            :options="apps.map((a) => ({ label: a.name, value: String(a.id) }))"
            @change="loadAuth"
          />
          <AInput v-model:value="query.keyword" placeholder="路径/说明" style="width: 200px" />
          <ASelect
            v-model:value="query.authFilter"
            style="width: 130px"
            :options="[
              { label: '全部接口', value: 'all' },
              { label: '已授权', value: 'authorized' },
              { label: '未授权', value: 'unauthorized' },
            ]"
            @change="onFilterChange"
          />
          <AButton @click="onFilterChange">搜索</AButton>
        </div>
        <div class="flex gap-2">
          <label class="flex items-center gap-1 text-sm">
            <input
              type="checkbox"
              :checked="list.length > 0 && list.every((i) => checkedIds.includes(String(i.id)))"
              @change="toggleSelectPage"
            />
            本页全选
          </label>
          <AButton size="small" @click="selectAllFiltered">选中全部筛选结果</AButton>
          <AButton size="small" @click="clearChecked">清空勾选</AButton>
          <AButton @click="doSync">同步 Swagger 接口</AButton>
          <AButton :disabled="!appId || !checkedIds.length" @click="doGrant">批量授权勾选项</AButton>
          <AButton :disabled="!appId || !checkedIds.length" danger @click="doRevoke">批量取消勾选项</AButton>
          <AButton type="primary" :disabled="!appId" @click="save">全量保存</AButton>
        </div>
      </div>
      <AAlert
        v-if="!apps.length"
        class="mb-3"
        message="请先在「应用管理」创建应用，再进行接口授权"
        show-icon
        type="info"
      />
      <div v-else class="mb-3 text-xs opacity-60">
        已勾选 {{ checkedIds.length }} 个接口 · 共 {{ query.total }} 个接口 · 点击卡片查看完整说明
      </div>
      <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        <div
          v-for="item in list"
          :key="item.id"
          class="group cursor-pointer rounded-lg border p-3 transition-all hover:shadow-md hover:shadow-blue-500/10"
          @click="openDetail(item)"
        >
          <div class="mb-2 flex items-center gap-2">
            <input
              type="checkbox"
              :checked="checkedIds.includes(String(item.id))"
              @click.stop
              @change="toggleCheck(item)"
            />
            <ATag :color="METHOD_COLORS[item.method] || 'default'">{{ item.method }}</ATag>
            <span class="truncate font-mono text-xs font-medium" :title="item.path">
              {{ item.path }}
            </span>
          </div>
          <div class="mb-1 truncate text-sm" :title="item.summary">{{ item.summary || '（无说明）' }}</div>
          <div class="flex items-center justify-between text-xs opacity-60">
            <span>{{ item.tag }}</span>
            <span class="opacity-0 transition-opacity group-hover:opacity-100">查看详情 →</span>
          </div>
        </div>
      </div>
      <div class="mt-4 flex justify-end">
        <APagination
          :current="query.page"
          :page-size="query.pageSize"
          :total="query.total"
          :page-size-options="['12', '24', '48']"
          show-size-changer
          :show-total="showTotal"
          @change="onPageChange"
        />
      </div>
      <ADrawer
        v-model:open="detailOpen"
        placement="right"
        :title="detail ? `${detail.method} ${detail.path}` : '接口详情'"
        width="640"
      >
        <template v-if="detail">
          <div class="mb-4">
            <ATag :color="METHOD_COLORS[detail.method]">{{ detail.method }}</ATag>
            <span class="ml-2 font-mono text-sm">{{ detail.path }}</span>
          </div>
          <h4 class="mb-1 font-semibold">接口说明</h4>
          <p class="mb-1 text-sm">{{ detail.summary || '（无）' }}</p>
          <p class="mb-4 text-sm text-orange-500">{{ detail.description || '' }}</p>

          <h4 class="mb-2 font-semibold">请求参数（Query/Path）</h4>
          <ATable
            v-if="detail.parameters.length"
            :columns="paramColumns"
            :data-source="detail.parameters"
            :pagination="false"
            row-key="name"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'required'">
                <ATag v-if="record.required" color="red">必填</ATag>
                <span v-else>可选</span>
              </template>
            </template>
          </ATable>
          <p v-else class="text-sm opacity-60">无查询参数</p>

          <template v-if="detail.requestBody">
            <h4 class="mb-2 mt-4 font-semibold">请求体（JSON）</h4>
            <ATable
              v-if="detail.requestBody.fields.length"
              :columns="fieldColumns"
              :data-source="detail.requestBody.fields"
              :pagination="false"
              row-key="name"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'type'">
                  <span class="font-mono text-xs">{{ record.type }}</span>
                </template>
              </template>
            </ATable>
            <p v-else class="text-sm opacity-60">无结构化字段定义（Map 负载）</p>
          </template>

          <h4 class="mb-2 mt-4 font-semibold">响应</h4>
          <div v-for="r in detail.responses" :key="r.code" class="mb-3">
            <ATag :color="r.code === '200' ? 'green' : 'red'">{{ r.code }}</ATag>
            <span class="ml-1 text-sm">{{ r.description }}</span>
            <ATable
              v-if="r.fields && r.fields.length"
              :columns="fieldColumns"
              :data-source="r.fields"
              :pagination="false"
              row-key="name"
              size="small"
              class="mt-2"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'type'">
                  <span class="font-mono text-xs">{{ record.type }}</span>
                </template>
                <template v-else-if="column.key === 'name'">
                  <span :class="{ 'pl-4 opacity-80': record.name.startsWith('data.') }">
                    {{ record.name }}
                  </span>
                </template>
              </template>
            </ATable>
          </div>
        </template>
      </ADrawer>
    </div>
  </Page>
</template>
