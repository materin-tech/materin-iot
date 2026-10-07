<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import {
  Alert as AAlert,
  Button as AButton,
  Collapse as ACollapse,
  CollapsePanel as ACollapsePanel,
  Input as AInput,
  Pagination as APagination,
  message,
  Modal as AModal,
  Step as AStep,
  Steps as ASteps,
  Table as ATable,
  Tag as ATag,
} from 'ant-design-vue';

import {
  createApp,
  deleteApp,
  getAppCredential,
  getApiList,
  getAppList,
  getAuthorizedApis,
  resetSecret,
} from '#/api/developer';

interface AppItem {
  id: string;
  name: string;
  appKey: string;
  status: 0 | 1;
  remark?: string;
  createTime: string;
}

const loading = ref(false);
const list = ref<AppItem[]>([]);
const total = ref(0);
const query = reactive({ page: 1, pageSize: 20, name: '' });
const modalOpen = ref(false);
const saving = ref(false);
const step = ref(0);
const form = reactive({ name: '', remark: '' });
const created = ref<null | { appKey: string; appSecret: string }>(null);
const detailOpen = ref(false);
const detailLoading = ref(false);
const detailApp = ref<any>(null);
const detailApiList = ref<any[]>([]);
const detailPage = reactive({ page: 1, pageSize: 6 });
const copyTip = ref('');

async function load() {
  loading.value = true;
  try {
    const data: any = await getAppList(query);
    list.value = data.items;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

const detailApiPaged = computed(() => {
  const start = (detailPage.page - 1) * detailPage.pageSize;
  return detailApiList.value.slice(start, start + detailPage.pageSize);
});

function onDetailPageChange(page: number, pageSize: number) {
  detailPage.page = page;
  detailPage.pageSize = pageSize;
}

async function openDetail(row: AppItem) {
  detailOpen.value = true;
  detailLoading.value = true;
  detailApp.value = null;
  detailApiList.value = [];
  detailPage.page = 1;
  try {
    const [cred, authIds, allApis]: any[] = await Promise.all([
      getAppCredential(row.id),
      getAuthorizedApis(row.id),
      getApiList({ page: 1, pageSize: 999 }),
    ]);
    detailApp.value = { ...row, ...cred };
    const byId = new Map(allApis.items.map((a: any) => [String(a.id), a]));
    detailApiList.value = authIds.map((id: string) => byId.get(String(id))).filter(Boolean);
  } finally {
    detailLoading.value = false;
  }
}

function openCreate() {
  form.name = '';
  form.remark = '';
  created.value = null;
  step.value = 0;
  modalOpen.value = true;
}

async function save() {
  if (!form.name.trim()) {
    message.error('应用名不能为空');
    return;
  }
  saving.value = true;
  try {
    const res: any = await createApp({ name: form.name, remark: form.remark });
    created.value = res;
    step.value = 1;
    await load();
  } finally {
    saving.value = false;
  }
}

async function copyText(text: string, label: string) {
  try {
    await navigator.clipboard.writeText(text);
    copyTip.value = label;
    message.success(`${label} 已复制`);
    setTimeout(() => (copyTip.value = ''), 1500);
  } catch {
    message.error('复制失败，请手动选择复制');
  }
}

async function doResetSecret(row: AppItem) {
  AModal.confirm({
    content: '旧 SK 将立即失效，该应用的 MQTT 连接会全部断开，确认重置？',
    onOk: async () => {
      const res: any = await resetSecret(row.id);
      step.value = 1;
      created.value = { ...res, name: row.name };
      modalOpen.value = true;
      await load();
    },
    title: '重置 Secret',
  });
}

function doDelete(row: AppItem) {
  AModal.confirm({
    content: `确认删除应用「${row.name}」？授权关系将一并清除。`,
    onOk: async () => {
      await deleteApp(row.id);
      message.success('已删除');
      await load();
    },
    title: '删除应用',
  });
}

function onPageChange(page: number, pageSize: number) {
  query.page = page;
  query.pageSize = pageSize;
  load();
}

function showTotal(t: number) {
  return `共 ${t} 个应用`;
}

const TOPIC_ROWS = [
  ['materin/{productKey}/{deviceKey}/report', '订阅', '设备数据上报（实时透传）'],
  ['materin/{productKey}/{deviceKey}/event', '订阅', '设备事件/告警上报'],
  ['materin/{productKey}/{deviceKey}/reply', '订阅', '指令执行结果响应'],
  ['open/materin/{productKey}/{deviceKey}/cmd', '发布', '向设备下发指令（payload 需带 requestId）'],
];

onMounted(load);
</script>

<template>
  <Page>
    <div class="bg-background p-4 rounded-lg">
      <div class="mb-3 flex items-center justify-between">
        <div class="flex gap-2 items-center">
          <AInput v-model:value="query.name" placeholder="应用名" style="width: 200px" />
          <AButton type="primary" @click="load">搜索</AButton>
        </div>
        <AButton type="primary" @click="openCreate">新增应用</AButton>
      </div>

      <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        <div
          v-for="item in list"
          :key="item.id"
          class="group cursor-pointer rounded-lg border p-4 transition-all hover:shadow-md hover:shadow-blue-500/10"
          @click="openDetail(item)"
        >
          <div class="mb-2 flex items-center justify-between">
            <span class="text-base font-semibold">{{ item.name }}</span>
            <ATag :color="item.status === 1 ? 'green' : 'red'">
              {{ item.status === 1 ? '启用' : '禁用' }}
            </ATag>
          </div>
          <div class="mb-1 flex items-center gap-2 text-xs">
            <span class="opacity-60">AK</span>
            <code class="font-mono">{{ item.appKey }}</code>
          </div>
          <div class="mb-2 truncate text-xs opacity-70" :title="item.remark">
            {{ item.remark || '（无备注）' }}
          </div>
          <div class="flex items-center justify-between text-xs">
            <span class="opacity-50">{{ item.createTime }}</span>
            <span class="opacity-0 transition-opacity group-hover:opacity-100">查看详情 →</span>
          </div>
        </div>
      </div>

      <div class="mt-4 flex justify-end">
        <APagination
          :current="query.page"
          :page-size="query.pageSize"
          :total="total"
          :page-size-options="['9', '18', '36']"
          show-size-changer
          :show-total="showTotal"
          @change="onPageChange"
        />
      </div>

      <AModal
        :open="modalOpen"
        :title="created ? '应用凭证与接入指引' : '创建开发者应用'"
        width="720px"
        :footer="null"
        @cancel="modalOpen = false"
      >
        <ASteps :current="step" size="small" class="mb-4">
          <AStep title="填写信息" />
          <AStep title="保存凭证" />
          <AStep title="接入指引" />
        </ASteps>

        <div v-if="step === 0">
          <AAlert
            class="mb-4"
            message="创建后将生成 AK/SK 凭证对"
            description="AK 用于标识应用（MQTT username），SK 是私密凭证（MQTT password）。一个应用代表一个第三方接入方，可独立进行接口授权与凭证吊销。"
            show-icon
            type="info"
          />
          <AForm layout="vertical">
            <div class="mb-3">
              <div class="mb-1 text-sm font-medium">应用名 *</div>
              <AInput v-model:value="form.name" placeholder="如：智慧园区集成" />
            </div>
            <div>
              <div class="mb-1 text-sm font-medium">备注</div>
              <AInput v-model:value="form.remark" placeholder="应用用途/对接方说明" />
            </div>
          </AForm>
          <div class="flex justify-end gap-2 mt-2">
            <AButton @click="modalOpen = false">取消</AButton>
            <AButton type="primary" :loading="saving" @click="save">创建并获取凭证</AButton>
          </div>
        </div>

        <div v-else-if="step === 1 && created">
          <AAlert
            class="mb-4"
            message="SK 仅此一次展示，请立即保存"
            description="关闭本窗口后无法再次查看完整 SK；如泄露请回到列表使用「重置SK」。"
            show-icon
            type="warning"
          />
          <div class="mb-3 rounded border p-3">
            <div class="mb-1 flex items-center justify-between">
              <span class="text-sm font-medium">AK（MQTT username）</span>
              <AButton size="small" type="link" @click="copyText(created.appKey, 'AK')">复制</AButton>
            </div>
            <code class="text-sm">{{ created.appKey }}</code>
          </div>
          <div class="mb-4 rounded border p-3">
            <div class="mb-1 flex items-center justify-between">
              <span class="text-sm font-medium">SK（MQTT password）</span>
              <AButton size="small" type="link" @click="copyText(created.appSecret, 'SK')">复制</AButton>
            </div>
            <code class="text-xs break-all">{{ created.appSecret }}</code>
          </div>
          <div class="flex justify-end">
            <AButton type="primary" @click="step = 2">我已保存，查看接入指引</AButton>
          </div>
        </div>

        <div v-else-if="step === 2">
          <AAlert
            class="mb-3"
            message="下一步：去「接口授权」勾选该应用可访问的接口"
            type="success"
            show-icon
          />
          <ACollapse>
            <ACollapsePanel key="conn" header="1. MQTT 连接信息（EMQX-2 开发者接入）">
              <div class="text-sm leading-6">
                <div>地址：<code>ws://localhost:11883</code>（TCP）/ 管理台 <code>localhost:18084</code></div>
                <div>username：<code>{{ created?.appKey || '你的 AK' }}</code></div>
                <div>password：<code>你的 SK</code>（切勿写进前端代码）</div>
                <div class="opacity-70">鉴权由平台实时校验；仅允许访问 open/ 命名空间。</div>
              </div>
            </ACollapsePanel>
            <ACollapsePanel key="topic" header="2. Topic 规范">
              <ATable
                :columns="[{ title: 'Topic', dataIndex: 0 }, { title: '操作', dataIndex: 1, width: 70 }, { title: '说明', dataIndex: 2 }]"
                :data-source="TOPIC_ROWS"
                :pagination="false"
                size="small"
              />
            </ACollapsePanel>
            <ACollapsePanel key="sample" header="3. 连接示例（mosquitto）">
              <pre class="text-xs leading-5 bg-black/80 text-green-300 p-2 rounded overflow-auto">mosquitto_sub -h localhost -p 11883 \
  -u "你的AK" -P "你的SK" \
  -t "open/materin/+/+/report"</pre>
            </ACollapsePanel>
          </ACollapse>
          <div class="flex justify-end mt-3">
            <AButton type="primary" @click="modalOpen = false">完成</AButton>
          </div>
        </div>
      </AModal>

      <AModal
        v-model:open="detailOpen"
        :title="detailApp ? `${detailApp.name} — 应用详情` : '应用详情'"
        width="860px"
        :footer="null"
      >
        <template v-if="detailApp">
          <h4 class="mb-2 font-semibold">应用信息与凭证</h4>
          <div class="mb-4 rounded border p-3 text-sm">
            <div class="mb-2 flex items-center justify-between">
              <span class="opacity-60">AK（MQTT username）</span>
              <AButton size="small" type="link" @click="copyText(detailApp.appKey, 'AK')">复制</AButton>
            </div>
            <code class="font-mono text-xs">{{ detailApp.appKey }}</code>
            <div class="mb-1 mt-3 flex items-center justify-between">
              <span class="opacity-60">SK（MQTT password）</span>
              <AButton size="small" type="link" @click="copyText(detailApp.appSecret, 'SK')">复制</AButton>
            </div>
            <code class="break-all font-mono text-xs">{{ detailApp.appSecret }}</code>
            <div class="mt-3 flex gap-2">
              <AButton size="small" @click="doResetSecret(detailApp)">重置SK</AButton>
              <AButton size="small" danger @click="doDelete(detailApp)">删除应用</AButton>
            </div>
          </div>

          <AAlert type="info" show-icon class="mb-4">
            <template #message>开发者接入流程</template>
            <template #description>
              <div class="text-sm leading-6">
                <b>1.</b> 创建应用获取 AK/SK 凭证 →
                <b>2.</b> 在「接口授权」勾选该应用可访问的接口 →
                <b>3.</b> 使用 AK/SK 连接开发者 MQTT（<code>localhost:11883</code>，username=AK，password=SK）→
                <b>4.</b> 按 Topic 规范订阅设备数据 / 下发指令。
                鉴权失败或访问未授权命名空间将被拒绝；SK 仅创建时展示一次，泄露请立即重置。
              </div>
            </template>
          </AAlert>

          <h4 class="mb-2 font-semibold">鉴权逻辑</h4>
          <div class="mb-4 rounded border p-3 text-sm leading-6">
            <div><b>1. 连接：</b>开发者 MQTT（EMQX-2）<code>localhost:11883</code>，username=本应用 AK，password=本应用 SK，由平台实时校验，错误即拒连。</div>
            <div><b>2. 授权：</b>仅允许访问 <code>open/</code> 命名空间；可访问的 HTTP 接口由「接口授权」勾选决定，未授权调用将被拒绝。</div>
            <div><b>3. 吊销：</b>重置 SK 后旧凭证立即失效；删除应用即收回全部权限。</div>
          </div>

          <h4 class="mb-2 font-semibold">使用示例（当前 AK）</h4>
          <pre class="mb-4 overflow-auto rounded bg-black/80 p-2 text-xs leading-5 text-green-300">mosquitto_sub -h localhost -p 11883 \
  -u "{{ detailApp.appKey }}" -P "{{ detailApp.appSecret }}" \
  -t "open/materin/+/+/report"

mosquitto_pub -h localhost -p 11883 \
  -u "{{ detailApp.appKey }}" -P "{{ detailApp.appSecret }}" \
  -t "open/materin/{productKey}/{deviceKey}/cmd" \
  -m '{"requestId":"your-req-001","action":"reboot"}'</pre>

          <h4 class="mb-2 font-semibold">已授权接口（{{ detailApiList.length }}）</h4>
          <div v-if="detailApiList.length" class="grid gap-2">
            <div
              v-for="api in detailApiPaged"
              :key="api.id"
              class="flex items-center gap-2 rounded border p-2 text-xs"
            >
              <ATag :color="api.method === 'GET' ? 'green' : api.method === 'POST' ? 'blue' : api.method === 'PUT' ? 'orange' : 'red'">
                {{ api.method }}
              </ATag>
              <span class="truncate font-mono">{{ api.path }}</span>
              <span class="ml-auto shrink-0 opacity-60">{{ api.summary }}</span>
            </div>
            <div class="flex justify-end">
              <APagination
                :current="detailPage.page"
                :page-size="detailPage.pageSize"
                :total="detailApiList.length"
                size="small"
                :show-size-changer="false"
                @change="onDetailPageChange"
              />
            </div>
          </div>
          <AAlert
            v-else
            message="该应用还没有授权任何接口，请到「接口授权」页勾选"
            show-icon
            type="warning"
          />
        </template>
      </AModal>
    </div>
  </Page>
</template>
