<script lang="ts" setup>
import type { ThingModelApi } from '#/api/product/thing-model';

import { computed, reactive, ref, watch } from 'vue';

import {
  Button,
  Checkbox,
  Drawer,
  Input,
  InputNumber,
  message,
  Select,
  Textarea,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  ACCESS_MODE_OPTIONS,
  CATEGORY_OPTIONS,
  CALL_TYPE_OPTIONS,
  DATA_TYPE_OPTIONS,
  EVENT_LEVEL_OPTIONS,
  THING_MODEL_SECTIONS,
  supportsRange,
  type SectionKey,
} from '../data';
import ParamFields from './param-fields.vue';

const props = defineProps<{
  /** 编辑的既有条目，新增时传 null */
  item: null | Record<string, any>;
  section: SectionKey;
}>();

const open = defineModel<boolean>('open', { default: false });

const emits = defineEmits<{
  save: [item: Record<string, any>];
}>();

const draft = reactive<Record<string, any>>({});

const sectionLabel = computed(
  () =>
    THING_MODEL_SECTIONS.find((s) => s.itemsKey === props.section)?.label ??
    props.section,
);

const getDrawerTitle = computed(() => {
  return props.item
    ? $t('product.thingModel.editSectionItem', [sectionLabel.value])
    : $t('product.thingModel.addSectionItem', [sectionLabel.value]);
});

/** 抽屉宽度：固定像素值（不自适应），支持拖拽调节 */
const MIN_WIDTH = 440;
const MAX_WIDTH = 920;
const width = ref(560);
const dragging = ref(false);

function onDragStart(e: MouseEvent) {
  e.preventDefault();
  dragging.value = true;
  const startX = e.clientX;
  const startWidth = width.value;
  const onMove = (ev: MouseEvent) => {
    const next = startWidth + (startX - ev.clientX);
    width.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, next));
  };
  const onUp = () => {
    dragging.value = false;
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
  };
  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
}

/** 打开时按传入条目重置草稿（Vue 响应式 proxy 不能 structuredClone，用 JSON 深拷贝） */
watch(open, (val) => {
  if (!val) return;
  const base = props.item
    ? JSON.parse(JSON.stringify(props.item))
    : defaultItem(props.section);
  Object.keys(draft).forEach((key) => delete draft[key]);
  Object.assign(draft, base);
  // enum/bool 的枚举项兜底
  if (props.section === 'properties' && !draft.enumItems) {
    draft.enumItems = [];
  }
  if (props.section === 'properties' && !draft.params) {
    draft.params = [];
  }
});

/** 切换数据类型时保证枚举项结构存在（bool 固定 0/1 两行） */
watch(
  () => draft.dataType,
  (type) => {
    if (props.section !== 'properties') return;
    if ((type === 'bool' || type === 'enum') && !draft.enumItems) {
      draft.enumItems = [];
    }
    if (type === 'bool') {
      draft.enumItems = [
        { value: 0, description: draft.enumItems[0]?.description ?? '' },
        { value: 1, description: draft.enumItems[1]?.description ?? '' },
      ];
    }
  },
);

function defaultItem(section: SectionKey): Record<string, any> {
  switch (section) {
    case 'events': {
      return { level: 'info', name: '', identifier: '', outputParams: [] };
    }
    case 'methods': {
      return {
        callType: 'sync',
        inputParams: [],
        name: '',
        identifier: '',
        outputParam: null,
      };
    }
    default: {
      return {
        accessMode: 'rw',
        category: undefined,
        enumItems: [],
        name: '',
        identifier: '',
        dataType: undefined,
        params: [],
      };
    }
  }
}

/** 参数小编辑器 */
function addParam(list: ThingModelApi.ParamItem[]) {
  list.push({ identifier: '', name: '' });
}

function removeParam(list: ThingModelApi.ParamItem[], index: number) {
  list.splice(index, 1);
}

/** 枚举项编辑器（enum 手动增删；bool 固定 0/1 两项） */
function addEnumItem() {
  draft.enumItems.push({ description: '', value: '' });
}

function removeEnumItem(index: number) {
  draft.enumItems = draft.enumItems.filter(
    (_item: any, i: number) => i !== index,
  );
}

function isParamValid(param: ThingModelApi.ParamItem): boolean {
  const basic = Boolean(param.name?.trim() && param.identifier?.trim());
  if (!basic) return false;
  // enum 参数：至少一个枚举项（值+说明）；bool 参数：0/1 说明必填
  if (param.dataType === 'enum') {
    return (
      (param.enumItems?.length ?? 0) > 0 &&
      (param.enumItems ?? []).every(
        (item: any) => item.value !== '' && item.description?.trim(),
      )
    );
  }
  if (param.dataType === 'bool') {
    return (param.enumItems ?? []).every((item: any) =>
      item.description?.trim(),
    );
  }
  // struct 参数：递归校验子字段
  if (param.dataType === 'struct') {
    return (param.params ?? []).every((item: any) => isParamValid(item));
  }
  return true;
}

function isEnumItemsValid(): boolean {
  if (draft.dataType === 'bool') {
    return draft.enumItems.every((item: any) => item.description?.trim());
  }
  if (draft.dataType === 'enum') {
    return (
      draft.enumItems.length > 0 &&
      draft.enumItems.every(
        (item: any) => item.value !== '' && item.description?.trim(),
      )
    );
  }
  return true;
}

function isStructParamsValid(): boolean {
  return draft.dataType !== 'struct' || draft.params.every(isParamValid);
}

const paramsValid = computed(() => {
  if (props.section === 'methods') {
    return (
      (draft.inputParams ?? []).every(isParamValid) &&
      (!draft.outputParam || isParamValid(draft.outputParam))
    );
  }
  if (props.section === 'events') {
    return (draft.outputParams ?? []).every(isParamValid);
  }
  return true;
});

const basicValid = computed(() => {
  const ok =
    Boolean(draft.name?.trim() && draft.identifier?.trim()) &&
    paramsValid.value;
  if (props.section === 'properties') {
    return (
      ok &&
      Boolean(draft.category) &&
      Boolean(draft.dataType) &&
      isEnumItemsValid() &&
      isStructParamsValid()
    );
  }
  return ok;
});

function onSave() {
  // 校验失败时提示并保持抽屉打开
  if (!basicValid.value) {
    if (props.section === 'properties' && !isEnumItemsValid()) {
      message.error($t('product.thingModel.enumRequired'));
    } else {
      message.error($t('product.thingModel.itemInvalid', [sectionLabel.value]));
    }
    return;
  }
  // bool 固定取值 0/1
  if (props.section === 'properties' && draft.dataType === 'bool') {
    draft.enumItems = [
      { value: 0, description: draft.enumItems[0]?.description ?? '' },
      { value: 1, description: draft.enumItems[1]?.description ?? '' },
    ];
  }
  emits('save', JSON.parse(JSON.stringify(draft)));
  open.value = false;
}
</script>
<template>
  <Drawer
    v-model:open="open"
    :width="width"
    :root-class-name="dragging ? 'drawer-no-transition' : ''"
    :title="getDrawerTitle"
  >
    <!-- 左缘拖拽手柄 -->
    <div
      class="absolute inset-y-0 left-0 z-10 w-1.5 cursor-ew-resize bg-transparent transition-colors hover:bg-primary/30"
      @mousedown="onDragStart"
    ></div>

    <!-- 基础字段：三要素共用 -->
    <div class="space-y-3">
      <div>
        <div class="mb-1 text-sm">{{ $t('product.thingModel.name') }} *</div>
        <Input
          v-model:value="draft.name"
          :placeholder="$t('product.thingModel.name')"
        />
      </div>
      <div>
        <div class="mb-1 text-sm">{{ $t('product.thingModel.identifier') }} *</div>
        <Input
          v-model:value="draft.identifier"
          :placeholder="'switch_status'"
        />
      </div>

      <!-- 属性：分类 / 数据类型 / 读写 / 分类型约束 -->
      <template v-if="section === 'properties'">
        <div>
          <div class="mb-1 text-sm">{{ $t('product.thingModel.category') }} *</div>
          <Select
            v-model:value="draft.category"
            :options="CATEGORY_OPTIONS"
            class="w-full"
            :placeholder="$t('product.thingModel.category')"
          />
        </div>
        <div>
          <div class="mb-1 text-sm">{{ $t('product.thingModel.dataType') }} *</div>
          <Select
            v-model:value="draft.dataType"
            :options="DATA_TYPE_OPTIONS"
            class="w-full"
            :placeholder="$t('product.thingModel.dataType')"
          />
        </div>
        <div>
          <div class="mb-1 text-sm">{{ $t('product.thingModel.accessMode') }}</div>
          <Select
            v-model:value="draft.accessMode"
            :options="[...ACCESS_MODE_OPTIONS]"
            class="w-full"
          />
        </div>

        <!-- int/double：区间 + 步长 + 单位 -->
        <div v-if="supportsRange(draft.dataType)" class="rounded-md border p-3">
          <div class="text-muted-foreground mb-2 text-xs">
            {{ $t('product.thingModel.constraints') }}
          </div>
          <div class="flex items-center gap-2">
            <InputNumber v-model:value="draft.min" class="flex-1" placeholder="min" />
            <span class="text-muted-foreground">~</span>
            <InputNumber v-model:value="draft.max" class="flex-1" placeholder="max" />
            <InputNumber v-model:value="draft.step" class="w-20" placeholder="step" :min="0" />
          </div>
          <Input
            v-model:value="draft.unit"
            class="mt-2"
            :placeholder="$t('product.thingModel.constraints') + ' · 单位（如 ℃ / % / kWh）'"
          />
        </div>

        <!-- string：最大长度 -->
        <div v-else-if="draft.dataType === 'string'" class="rounded-md border p-3">
          <div class="text-muted-foreground mb-2 text-xs">
            {{ $t('product.thingModel.constraints') }}
          </div>
          <InputNumber
            v-model:value="draft.maxLength"
            class="w-full"
            :placeholder="$t('product.thingModel.constraints') + ' · 最大长度'"
            :min="1"
          />
        </div>

        <!-- enum：枚举项（必填） -->
        <div v-else-if="draft.dataType === 'enum'" class="rounded-md border p-3">
          <div class="text-muted-foreground mb-2 text-xs">
            {{ $t('product.thingModel.enumItems') }} *
          </div>
          <div class="space-y-2">
            <div
              v-for="(item, index) in draft.enumItems"
              :key="index"
              class="flex items-center gap-2"
            >
              <Input
                v-model:value="item.value"
                class="w-24"
                :placeholder="$t('product.thingModel.enumValue')"
              />
              <Input
                v-model:value="item.description"
                class="flex-1"
                :placeholder="$t('product.thingModel.enumDesc')"
              />
              <Button danger size="small" type="text" @click="removeEnumItem(index as number)">
                {{ $t('common.delete') }}
              </Button>
            </div>
            <Button class="w-full" dashed size="small" @click="addEnumItem">
              {{ $t('product.thingModel.addEnumItem') }}
            </Button>
          </div>
        </div>

        <!-- bool：固定 0/1 两项说明 -->
        <div v-else-if="draft.dataType === 'bool'" class="rounded-md border p-3">
          <div class="text-muted-foreground mb-2 text-xs">
            {{ $t('product.thingModel.boolItems') }}
          </div>
          <div class="space-y-2">
            <div class="flex items-center gap-2">
              <span class="w-6 shrink-0 text-center font-mono text-sm">0</span>
              <Input
                v-model:value="draft.enumItems[0].description"
                :placeholder="$t('product.thingModel.bool0Desc')"
              />
            </div>
            <div class="flex items-center gap-2">
              <span class="w-6 shrink-0 text-center font-mono text-sm">1</span>
              <Input
                v-model:value="draft.enumItems[1].description"
                :placeholder="$t('product.thingModel.bool1Desc')"
              />
            </div>
          </div>
        </div>

        <!-- struct：子参数列表 -->
        <div v-else-if="draft.dataType === 'struct'" class="rounded-md border p-3">
          <div class="text-muted-foreground mb-2 text-xs">
            {{ $t('product.thingModel.structParams') }}
          </div>
          <div class="space-y-2">
            <div
              v-for="(param, index) in draft.params"
              :key="index"
              class="bg-accent/30 rounded-md p-2"
            >
              <div class="flex items-center gap-2">
                <Input
                  v-model:value="param.name"
                  :placeholder="$t('product.thingModel.paramName')"
                  class="flex-1"
                  size="small"
                />
                <Input
                  v-model:value="param.identifier"
                  :placeholder="$t('product.thingModel.paramIdentifier')"
                  class="flex-1"
                  size="small"
                />
                <Select
                  v-model:value="param.dataType"
                  :options="DATA_TYPE_OPTIONS"
                  class="w-24"
                  size="small"
                  :placeholder="$t('product.thingModel.dataType')"
                />
                <Button danger size="small" type="text" @click="removeParam(draft.params, index as number)">
                  {{ $t('common.delete') }}
                </Button>
              </div>
              <ParamFields :param="param" />
            </div>
            <Button class="w-full" dashed size="small" @click="addParam(draft.params)">
              {{ $t('product.thingModel.addParam') }}
            </Button>
          </div>
        </div>
      </template>

      <!-- 方法：调用方式 / 入参 / 返回值 -->
      <template v-else-if="section === 'methods'">
        <div>
          <div class="mb-1 text-sm">{{ $t('product.thingModel.callType') }}</div>
          <Select
            v-model:value="draft.callType"
            :options="[...CALL_TYPE_OPTIONS]"
            class="w-full"
          />
        </div>

        <div class="rounded-md border p-3">
          <div class="text-muted-foreground mb-2 text-xs">
            {{ $t('product.thingModel.inputParams') }}
          </div>
          <div class="space-y-2">
            <div
              v-for="(param, index) in draft.inputParams"
              :key="index"
              class="bg-accent/30 rounded-md p-2"
            >
              <div class="flex items-center gap-2">
                <Input
                  v-model:value="param.name"
                  :placeholder="$t('product.thingModel.paramName')"
                  class="flex-1"
                  size="small"
                />
                <Input
                  v-model:value="param.identifier"
                  :placeholder="$t('product.thingModel.paramIdentifier')"
                  class="flex-1"
                  size="small"
                />
                <Select
                  v-model:value="param.dataType"
                  :options="DATA_TYPE_OPTIONS"
                  class="w-24"
                  size="small"
                  :placeholder="$t('product.thingModel.dataType')"
                />
                <Button danger size="small" type="text" @click="removeParam(draft.inputParams, index as number)">
                  {{ $t('common.delete') }}
                </Button>
              </div>
              <ParamFields :param="param" />
            </div>
            <Button class="w-full" dashed size="small" @click="addParam(draft.inputParams)">
              {{ $t('product.thingModel.addParam') }}
            </Button>
          </div>
        </div>

        <div class="rounded-md border p-3">
          <div class="flex items-center justify-between">
            <div class="text-muted-foreground text-xs">
              {{ $t('product.thingModel.outputParam') }}
            </div>
            <Checkbox
              :checked="!draft.outputParam"
              @change="(e: any) => (draft.outputParam = e.target.checked ? null : {})"
            >
              {{ $t('product.thingModel.noOutputParam') }}
            </Checkbox>
          </div>
          <div v-if="draft.outputParam" class="mt-2">
            <div class="flex items-center gap-2">
              <Input
                v-model:value="draft.outputParam.name"
                :placeholder="$t('product.thingModel.paramName')"
                class="flex-1"
                size="small"
              />
              <Input
                v-model:value="draft.outputParam.identifier"
                :placeholder="$t('product.thingModel.paramIdentifier')"
                class="flex-1"
                size="small"
              />
              <Select
                v-model:value="draft.outputParam.dataType"
                :options="DATA_TYPE_OPTIONS"
                class="w-24"
                size="small"
                :placeholder="$t('product.thingModel.dataType')"
              />
            </div>
            <ParamFields :param="draft.outputParam" />
          </div>
        </div>
      </template>

      <!-- 事件：级别 / 返回结果参数 -->
      <template v-else>
        <div>
          <div class="mb-1 text-sm">{{ $t('product.thingModel.level') }}</div>
          <Select
            v-model:value="draft.level"
            :options="[...EVENT_LEVEL_OPTIONS]"
            class="w-full"
          />
        </div>
        <div class="rounded-md border p-3">
          <div class="text-muted-foreground mb-2 text-xs">
            {{ $t('product.thingModel.outputParams') }}
          </div>
          <div class="space-y-2">
            <div
              v-for="(param, index) in draft.outputParams"
              :key="index"
              class="bg-accent/30 rounded-md p-2"
            >
              <div class="flex items-center gap-2">
                <Input
                  v-model:value="param.name"
                  :placeholder="$t('product.thingModel.paramName')"
                  class="flex-1"
                  size="small"
                />
                <Input
                  v-model:value="param.identifier"
                  :placeholder="$t('product.thingModel.paramIdentifier')"
                  class="flex-1"
                  size="small"
                />
                <Select
                  v-model:value="param.dataType"
                  :options="DATA_TYPE_OPTIONS"
                  class="w-24"
                  size="small"
                  :placeholder="$t('product.thingModel.dataType')"
                />
                <Button danger size="small" type="text" @click="removeParam(draft.outputParams, index as number)">
                  {{ $t('common.delete') }}
                </Button>
              </div>
              <ParamFields :param="param" />
            </div>
            <Button class="w-full" dashed size="small" @click="addParam(draft.outputParams)">
              {{ $t('product.thingModel.addParam') }}
            </Button>
          </div>
        </div>
      </template>

      <div>
        <div class="mb-1 text-sm">
          {{
            section === 'properties'
              ? $t('product.thingModel.fieldDesc')
              : $t('product.remark')
          }}
        </div>
        <Textarea v-model:value="draft.remark" :rows="2" />
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <Button @click="open = false">{{ $t('common.cancel') }}</Button>
        <Button type="primary" @click="onSave">{{ $t('common.confirm') }}</Button>
      </div>
    </template>
  </Drawer>
</template>
<style>
.drawer-no-transition {
  transition: none !important;
}
</style>
