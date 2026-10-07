<script lang="ts" setup>
import type { ThingModelApi } from '#/api/product/thing-model';

import type { DeviceApi } from '#/api/device/device';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Input, Select, TabPane, Tabs, Tag } from 'ant-design-vue';

import { getDeviceList } from '#/api/device/device';
import { getThingModelList } from '#/api/product/thing-model';
import { $t } from '#/locales';

defineOptions({ name: 'DeviceDebug' });

interface LogEntry {
  payload?: string;
  reply?: string;
  time: string;
  title: string;
  type: 'error' | 'reply' | 'sent';
}

const devices = ref<DeviceApi.Device[]>([]);
const deviceId = ref<string>();
const model = ref<null | ThingModelApi.ThingModel>(null);
const loadingModel = ref(false);

/** 属性写入值 / 方法入参（按 identifier 暂存） */
const propertyValues = ref<Record<string, any>>({});
const methodParams = ref<Record<string, Record<string, any>>>({});
const sending = ref(false);
const activeSection = ref<'events' | 'methods' | 'properties'>('properties');
const logs = ref<LogEntry[]>([]);

const STATUS_META: Record<number, { color: string; label: string }> = {
  0: { color: 'default', label: $t('device.statusInactive') },
  1: { color: 'success', label: $t('device.statusActive') },
  2: { color: 'error', label: $t('device.statusOffline') },
};

const deviceOptions = computed(() =>
  devices.value.map((item) => ({
    label: `${item.name}（${STATUS_META[item.status]?.label}）`,
    value: item.id,
  })),
);

const currentDevice = computed(() =>
  devices.value.find((item) => item.id === deviceId.value),
);

const sectionMeta = [
  {
    itemsKey: 'properties' as const,
    label: $t('product.thingModel.typeProperty'),
  },
  {
    itemsKey: 'methods' as const,
    label: $t('product.thingModel.typeService'),
  },
  { itemsKey: 'events' as const, label: $t('product.thingModel.typeEvent') },
];

/** 选中设备后加载其产品的物模型 */
async function onDeviceChange() {
  model.value = null;
  propertyValues.value = {};
  methodParams.value = {};
  const device = currentDevice.value;
  if (!device) return;
  loadingModel.value = true;
  try {
    const res = await getThingModelList({ page: 1, pageSize: 200 });
    model.value =
      res.items.find((item) => item.productId === device.productId) ?? null;
  } finally {
    loadingModel.value = false;
  }
}

function setMethodParam(methodId: string, paramId: string, value: any) {
  methodParams.value[methodId] = {
    ...(methodParams.value[methodId] ?? {}),
    [paramId]: value,
  };
}

function now() {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false });
}

function pushLog(entry: LogEntry) {
  logs.value.unshift(entry);
}

/** 按数据类型生成模拟值 */
function genValue(dataType?: string): any {
  switch (dataType) {
    case 'bool': {
      return Math.random() > 0.5 ? 1 : 0;
    }
    case 'double': {
      return Number((Math.random() * 100).toFixed(1));
    }
    case 'int': {
      return Math.round(Math.random() * 100);
    }
    case 'struct': {
      return { note: 'sample' };
    }
    default: {
      return 'sample';
    }
  }
}

function genStructValue(params?: any[]): Record<string, any> {
  const obj: Record<string, any> = {};
  (params ?? []).forEach((p) => {
    obj[p.identifier || p.name] = genValue(p.dataType);
  });
  return obj;
}

/** 模拟设备响应（离线/未激活有差异化提示） */
function simulateReply(
  device: DeviceApi.Device,
  title: string,
  payload?: any,
): LogEntry {
  if (device.status === 0) {
    return {
      payload,
      reply: $t('device.debug.inactive'),
      time: now(),
      title,
      type: 'error',
    };
  }
  if (device.status === 2) {
    return {
      payload,
      reply: $t('device.debug.offline'),
      time: now(),
      title,
      type: 'error',
    };
  }
  return {
    payload,
    reply: JSON.stringify({ code: 0, msg: 'ok', data: payload ?? null }),
    time: now(),
    title,
    type: 'reply',
  };
}

async function runOperation(
  title: string,
  payload: any,
  successData?: () => any,
) {
  const device = currentDevice.value;
  if (!device) return;
  pushLog({ payload, time: now(), title, type: 'sent' });
  sending.value = true;
  setTimeout(() => {
    pushLog(
      device.status === 1 && successData
        ? {
            payload,
            reply: JSON.stringify({
              code: 0,
              msg: 'ok',
              data: successData(),
            }),
            time: now(),
            title,
            type: 'reply',
          }
        : simulateReply(device, title, payload),
    );
    sending.value = false;
  }, 500);
}

/** 属性：读取 */
function onReadProperty(item: ThingModelApi.PropertyItem) {
  runOperation(
    `${$t('product.thingModel.typeProperty')}·读取 ${item.name}(${item.identifier})`,
    { identifier: item.identifier },
    () => ({ [item.identifier]: genValue(item.dataType) }),
  );
}

/** 属性：写入（仅读写型） */
function onWriteProperty(item: ThingModelApi.PropertyItem) {
  const value =
    propertyValues.value[item.identifier] ?? genValue(item.dataType);
  runOperation(
    `${$t('product.thingModel.typeProperty')}·写入 ${item.name}(${item.identifier})`,
    { identifier: item.identifier, value },
    () => ({ [item.identifier]: value }),
  );
}

/** 方法：调用（入参按物模型定义生成表单） */
function onInvokeMethod(item: ThingModelApi.MethodItem) {
  const inputs: Record<string, any> = {};
  (item.inputParams ?? []).forEach((p) => {
    inputs[p.identifier || p.name] =
      methodParams.value[item.identifier]?.[p.identifier || p.name] ??
      (p.dataType === 'struct'
        ? genStructValue(p.params)
        : genValue(p.dataType));
  });
  runOperation(
    `${$t('product.thingModel.typeService')}·调用 ${item.name}(${item.identifier})`,
    { identifier: item.identifier, inputs },
    () =>
      item.outputParam
        ? { [item.outputParam.identifier || 'result']: genValue(item.outputParam.dataType) }
        : null,
  );
}

/** 事件：模拟上报 */
function onTriggerEvent(item: ThingModelApi.EventItem) {
  const output: Record<string, any> = {};
  (item.outputParams ?? []).forEach((p) => {
    output[p.identifier || p.name] =
      p.dataType === 'struct'
        ? genStructValue(p.params)
        : genValue(p.dataType);
  });
  runOperation(
    `${$t('product.thingModel.typeEvent')}·上报 ${item.name}(${item.identifier})`,
    { identifier: item.identifier, level: item.level, params: output },
    () => output,
  );
}

onMounted(async () => {
  const res = await getDeviceList({ page: 1, pageSize: 200 });
  devices.value = res.items;
});
</script>

<template>
  <Page :title="$t('page.device.debug')">
    <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
      <!-- 左：设备 + 物模型三要素调试 -->
      <div class="bg-card rounded-lg p-5 shadow-sm lg:col-span-2">
        <div class="mb-4 space-y-3">
          <Select
            v-model:value="deviceId"
            :options="deviceOptions"
            class="w-full"
            show-search
            option-filter-prop="label"
            :placeholder="$t('device.debug.pickDevice')"
            @change="onDeviceChange"
          />
          <div v-if="currentDevice" class="text-muted-foreground flex flex-wrap items-center gap-4 text-xs">
            <span class="flex items-center gap-1.5">
              <span class="icon-[lucide--fingerprint] size-3.5"></span>
              <span class="font-mono">{{ currentDevice.deviceKey }}</span>
            </span>
            <span class="flex items-center gap-1.5">
              <span class="icon-[lucide--package] size-3.5"></span>
              <span>{{ currentDevice.productName }}</span>
            </span>
            <Tag :color="STATUS_META[currentDevice.status]?.color" class="!m-0">
              {{ STATUS_META[currentDevice.status]?.label }}
            </Tag>
          </div>
        </div>

        <div
          v-if="!currentDevice || !model"
          class="text-muted-foreground flex flex-col items-center gap-2 py-16 text-sm"
        >
          <span class="icon-[lucide--boxes] size-10 text-primary/30"></span>
          {{
            loadingModel
              ? '...'
              : $t('device.debug.pickDevice') + ' / ' + $t('page.product.thingModel')
          }}
        </div>

        <Tabs v-else v-model:active-key="activeSection">
          <TabPane
            v-for="section in sectionMeta"
            :key="section.itemsKey"
          >
            <template #tab>
              {{ section.label }}
              <span class="text-muted-foreground ml-1">
                {{ model[section.itemsKey]?.length ?? 0 }}
              </span>
            </template>

            <div class="max-h-[480px] space-y-2 overflow-y-auto pr-1">
              <!-- 属性 -->
              <template v-if="section.itemsKey === 'properties'">
                <div
                  v-for="item in model.properties"
                  :key="item.identifier"
                  class="bg-accent/20 flex items-center justify-between gap-3 rounded-md border p-3"
                >
                  <div class="min-w-0">
                    <div class="flex items-center gap-2">
                      <span class="text-foreground text-sm font-medium">{{ item.name }}</span>
                      <span class="text-muted-foreground font-mono text-xs">{{ item.identifier }}</span>
                      <Tag class="!m-0" color="geekblue">{{ item.dataType }}</Tag>
                      <Tag v-if="item.accessMode === 'r'" class="!m-0" color="default">
                        {{ $t('device.debug.readonly') || '只读' }}
                      </Tag>
                    </div>
                    <div class="text-muted-foreground mt-0.5 text-xs">
                      {{ item.category }}<template v-if="item.unit"> · {{ item.unit }}</template>
                    </div>
                  </div>
                  <div class="flex shrink-0 items-center gap-2">
                    <Input
                      v-if="item.accessMode !== 'r'"
                      v-model:value="propertyValues[item.identifier]"
                      size="small"
                      class="w-28"
                      :placeholder="$t('device.debug.write')"
                    />
                    <Button
                      v-if="item.accessMode !== 'r'"
                      size="small"
                      :disabled="sending"
                      @click="onWriteProperty(item)"
                    >
                      {{ $t('device.debug.write') }}
                    </Button>
                    <Button size="small" :disabled="sending" @click="onReadProperty(item)">
                      {{ $t('device.debug.read') }}
                    </Button>
                  </div>
                </div>
              </template>

              <!-- 方法 -->
              <template v-else-if="section.itemsKey === 'methods'">
                <div
                  v-for="item in model.methods"
                  :key="item.identifier"
                  class="bg-accent/20 rounded-md border p-3"
                >
                  <div class="flex items-center justify-between gap-3">
                    <div class="flex items-center gap-2">
                      <span class="text-foreground text-sm font-medium">{{ item.name }}</span>
                      <span class="text-muted-foreground font-mono text-xs">{{ item.identifier }}</span>
                      <Tag class="!m-0" color="purple">
                        {{ item.callType === 'async' ? $t('product.thingModel.callAsync') : $t('product.thingModel.callSync') }}
                      </Tag>
                    </div>
                    <Button
                      size="small"
                      type="primary"
                      :disabled="sending"
                      @click="onInvokeMethod(item)"
                    >
                      {{ $t('device.debug.invoke') || '调用' }}
                    </Button>
                  </div>
                  <div v-if="(item.inputParams ?? []).length > 0" class="mt-2 flex flex-wrap items-center gap-2">
                    <span class="text-muted-foreground text-xs">{{ $t('product.thingModel.inputParams') }}:</span>
                    <Input
                      v-for="p in item.inputParams"
                      :key="p.identifier"
                      :value="methodParams[item.identifier]?.[p.identifier]"
                      size="small"
                      class="w-32"
                      :placeholder="`${p.name}(${p.dataType})`"
                      @update:value="(v: any) => setMethodParam(item.identifier, p.identifier, v)"
                    />
                  </div>
                </div>
              </template>

              <!-- 事件 -->
              <template v-else>
                <div
                  v-for="item in model.events"
                  :key="item.identifier"
                  class="bg-accent/20 flex items-center justify-between gap-3 rounded-md border p-3"
                >
                  <div class="min-w-0">
                    <div class="flex items-center gap-2">
                      <span class="text-foreground text-sm font-medium">{{ item.name }}</span>
                      <span class="text-muted-foreground font-mono text-xs">{{ item.identifier }}</span>
                      <Tag class="!m-0" color="warning">{{ item.level }}</Tag>
                    </div>
                    <div class="text-muted-foreground mt-0.5 text-xs">
                      {{ $t('product.thingModel.outputParams') }} {{ item.outputParams?.length ?? 0 }}
                    </div>
                  </div>
                  <Button size="small" :disabled="sending" @click="onTriggerEvent(item)">
                    {{ $t('device.debug.trigger') || '模拟上报' }}
                  </Button>
                </div>
              </template>

              <div
                v-if="(model[section.itemsKey] ?? []).length === 0"
                class="text-muted-foreground py-8 text-center text-xs"
              >
                {{ $t('product.thingModel.emptySection') }}
              </div>
            </div>
          </TabPane>
        </Tabs>
      </div>

      <!-- 右：调试日志 -->
      <div class="bg-card flex flex-col rounded-lg p-5 shadow-sm">
        <div class="text-foreground mb-3 text-sm font-medium">
          {{ $t('device.debug.log') }}
        </div>
        <div
          v-if="logs.length === 0"
          class="text-muted-foreground flex flex-1 items-center justify-center py-16 text-sm"
        >
          {{ $t('device.debug.logEmpty') }}
        </div>
        <div v-else class="max-h-[560px] space-y-2 overflow-y-auto">
          <div
            v-for="(log, index) in logs"
            :key="index"
            class="bg-accent/30 rounded-md p-3 font-mono text-xs"
          >
            <div class="flex items-center justify-between">
              <Tag
                :color="log.type === 'reply' ? 'success' : log.type === 'error' ? 'error' : 'processing'"
                class="!m-0"
              >
                {{ log.type === 'reply' ? $t('device.debug.reply') : log.type === 'error' ? 'ERROR' : $t('device.debug.sent') }}
              </Tag>
              <span class="text-muted-foreground">{{ log.time }}</span>
            </div>
            <div class="mt-2 break-all">{{ log.title }}</div>
            <div v-if="log.payload" class="text-muted-foreground mt-1 break-all">
              {{ JSON.stringify(log.payload) }}
            </div>
            <div v-if="log.reply" class="mt-1 break-all" :class="log.type === 'error' ? 'text-destructive' : 'text-muted-foreground'">
              {{ log.reply }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </Page>
</template>
