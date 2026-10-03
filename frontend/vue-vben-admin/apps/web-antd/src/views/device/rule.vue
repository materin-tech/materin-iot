<script lang="ts" setup>
import type { DeviceRuleApi } from '#/api/device/rule';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Button,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  createDeviceRule,
  deleteDeviceRule,
  getDeviceRuleList,
  updateDeviceRule,
} from '#/api/device/rule';
import { $t } from '#/locales';

defineOptions({ name: 'DeviceRule' });

const Textarea = Input.TextArea;

const items = ref<DeviceRuleApi.DeviceRule[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({ page: 1, pageSize: 10 });

/** 触发/动作可选项（mock 语义：结构化下拉，后续接规则引擎） */
const TRIGGER_OPTIONS = [
  '设备属性上报触发',
  '设备离线触发',
  '定时触发（每小时）',
  '事件告警触发',
].map((value) => ({ label: value, value }));

const ACTION_OPTIONS = [
  '发送告警通知',
  '执行场景：打开排风',
  '下发设备命令',
  '推送 Webhook',
].map((value) => ({ label: value, value }));

async function fetchList() {
  loading.value = true;
  try {
    const res = await getDeviceRuleList({
      page: query.page,
      pageSize: query.pageSize,
    });
    items.value = res.items;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function onTableChange(p: any) {
  query.page = p.current;
  query.pageSize = p.pageSize;
  fetchList();
}

function onToggle(row: any, checked: boolean) {
  updateDeviceRule(row.id, { status: checked ? 1 : 0 }).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    row.status = checked ? 1 : 0;
  });
}

// ---------- 新增/编辑 ----------
const formOpen = ref(false);
const formMode = ref<'create' | 'edit'>('create');
const formData = reactive({
  action: undefined as string | undefined,
  id: undefined as string | undefined,
  name: '',
  remark: '',
  trigger: undefined as string | undefined,
});

function onEdit(row: any) {
  formMode.value = 'edit';
  Object.assign(formData, {
    action: row.action,
    id: row.id,
    name: row.name,
    remark: row.remark ?? '',
    trigger: row.trigger,
  });
  formOpen.value = true;
}

const formTitle = ref('');

function openForm() {
  formTitle.value =
    formMode.value === 'edit'
      ? $t('common.edit', $t('page.device.rule'))
      : $t('common.create', $t('page.device.rule'));
  formOpen.value = true;
}

async function onSave() {
  if (!formData.name.trim() || !formData.trigger || !formData.action) {
    message.error(
      $t('device.ruleStatus') + '：' + $t('device.trigger') + ' / ' + $t('device.action') + ' 必填',
    );
    return;
  }
  const payload = {
    action: formData.action,
    name: formData.name,
    remark: formData.remark,
    trigger: formData.trigger,
  };
  if (formMode.value === 'edit') {
    await updateDeviceRule(formData.id!, payload);
  } else {
    await createDeviceRule(payload as any);
  }
  message.success($t('ui.actionMessage.operationSuccess'));
  formOpen.value = false;
  fetchList();
}

function onDelete(row: any) {
  deleteDeviceRule(row.id).then(() => {
    message.success($t('ui.actionMessage.operationSuccess'));
    fetchList();
  });
}

const columns = [
  { dataIndex: 'name', title: $t('product.name'), width: 180 },
  { dataIndex: 'trigger', title: $t('device.trigger'), width: 200 },
  { dataIndex: 'action', title: $t('device.action') },
  { dataIndex: 'status', title: $t('device.ruleStatus'), width: 90 },
  { dataIndex: 'createTime', title: $t('product.createTime'), width: 170 },
  { dataIndex: 'operation', title: $t('product.operation'), width: 130 },
];

onMounted(() => {
  fetchList();
});
</script>

<template>
  <Page :title="$t('page.device.rule')">
    <div class="bg-card rounded-lg shadow-sm">
      <div class="flex flex-wrap items-center gap-3 p-4">
        <div class="text-muted-foreground text-xs">
          {{ $t('page.device.rule') }}（{{ total }}）
        </div>
        <div class="flex-1"></div>
        <Button
          type="primary"
          @click="() => { formMode = 'create'; openForm(); }"
        >
          <Plus class="size-5" />
          {{ $t('ui.actionTitle.create', [$t('page.device.rule')]) }}
        </Button>
      </div>

      <div class="px-4 pb-4">
        <Table
          :columns="columns"
          :data-source="items"
          :loading="loading"
          :pagination="{
            current: query.page,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            showTotal: (t: number) => $t('product.thingModel.totalItems', [t]),
          }"
          row-key="id"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'status'">
              <Switch
                :checked="record.status === 1"
                size="small"
                @change="(checked: any) => onToggle(record, checked)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'trigger'">
              <Tag color="geekblue" class="!m-0">{{ record.trigger }}</Tag>
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <Tag color="purple" class="!m-0">{{ record.action }}</Tag>
            </template>
            <template v-else-if="column.dataIndex === 'operation'">
              <Button type="link" size="small" @click="() => { formMode = 'edit'; onEdit(record); openForm(); }">
                {{ $t('common.edit') }}
              </Button>
              <Popconfirm
                :title="$t('ui.actionMessage.deleteConfirm', [record.name])"
                @confirm="onDelete(record)"
              >
                <Button type="link" size="small" danger>
                  {{ $t('common.delete') }}
                </Button>
              </Popconfirm>
            </template>
          </template>
        </Table>
      </div>
    </div>

    <Modal v-model:open="formOpen" :title="formTitle" destroy-on-close>
      <div class="space-y-3 py-2">
        <div>
          <div class="mb-1 text-sm">{{ $t('product.name') }} *</div>
          <Input v-model:value="formData.name" placeholder="联动规则-XXXX" />
        </div>
        <div>
          <div class="mb-1 text-sm">{{ $t('device.trigger') }} *</div>
          <Select
            v-model:value="formData.trigger"
            :options="TRIGGER_OPTIONS"
            class="w-full"
            :placeholder="$t('device.trigger')"
          />
        </div>
        <div>
          <div class="mb-1 text-sm">{{ $t('device.action') }} *</div>
          <Select
            v-model:value="formData.action"
            :options="ACTION_OPTIONS"
            class="w-full"
            :placeholder="$t('device.action')"
          />
        </div>
        <div>
          <div class="mb-1 text-sm">{{ $t('device.remark') }}</div>
          <Textarea v-model:value="formData.remark" :rows="2" />
        </div>
      </div>
      <template #footer>
        <Button @click="formOpen = false">{{ $t('common.cancel') }}</Button>
        <Button type="primary" @click="onSave">{{ $t('common.confirm') }}</Button>
      </template>
    </Modal>
  </Page>
</template>
