<script lang="ts" setup>
import { watch } from 'vue';

import { Button, Input, InputNumber, Select } from 'ant-design-vue';

import { $t } from '#/locales';

import { DATA_TYPE_OPTIONS, supportsRange } from '../data';

defineOptions({ name: 'ParamFields' });

/**
 * 参数约束编辑器（就地修改传入的 param 对象）：
 * - int/double：最小值 ~ 最大值 + 单位
 * - enum：枚举项（值 + 说明，必填）
 * - bool：固定 0/1 两行取值说明
 * - struct：子字段列表（递归，JSON 对象可多层嵌套）
 */
const props = defineProps<{
  param: Record<string, any>;
}>();

function addEnumItem() {
  props.param.enumItems = [
    ...(props.param.enumItems ?? []),
    { description: '', value: '' },
  ];
}

function removeEnumItem(index: number) {
  props.param.enumItems = props.param.enumItems.filter(
    (_item: any, i: number) => i !== index,
  );
}

function addSubParam() {
  props.param.params = [
    ...(props.param.params ?? []),
    { description: '', identifier: '', name: '' },
  ];
}

function removeSubParam(index: number) {
  props.param.params = props.param.params.filter(
    (_item: any, i: number) => i !== index,
  );
}

/** 切换数据类型时保证约束结构存在（bool 固定 0/1 两行，struct 保证子字段数组） */
watch(
  () => props.param.dataType,
  (type) => {
    if (type === 'bool') {
      props.param.enumItems = [
        {
          value: 0,
          description: props.param.enumItems?.[0]?.description ?? '',
        },
        {
          value: 1,
          description: props.param.enumItems?.[1]?.description ?? '',
        },
      ];
    } else if (type === 'enum' && !props.param.enumItems) {
      props.param.enumItems = [];
    } else if (type === 'struct' && !props.param.params) {
      props.param.params = [];
    }
  },
  { immediate: true },
);
</script>
<template>
  <!-- 参数说明（所有类型通用） -->
  <Input
    v-model:value="param.description"
    class="mt-2"
    size="small"
    :placeholder="$t('product.thingModel.paramDesc')"
  />

  <!-- int/double：数值区间 + 单位 -->
  <div
    v-if="supportsRange(param.dataType)"
    class="mt-2 flex items-center gap-2"
  >
    <InputNumber
      v-model:value="param.min"
      size="small"
      placeholder="min"
      class="flex-1"
    />
    <span class="text-muted-foreground">~</span>
    <InputNumber
      v-model:value="param.max"
      size="small"
      placeholder="max"
      class="flex-1"
    />
    <Input
      v-model:value="param.unit"
      class="w-20"
      size="small"
      placeholder="单位"
    />
  </div>

  <!-- enum：枚举项（值 + 说明） -->
  <div v-else-if="param.dataType === 'enum'" class="mt-2">
    <div class="text-muted-foreground mb-1 text-xs">
      {{ $t('product.thingModel.enumItems') }} *
    </div>
    <div class="space-y-1.5">
      <div
        v-for="(item, index) in param.enumItems"
        :key="index"
        class="flex items-center gap-1.5"
      >
        <Input
          v-model:value="item.value"
          class="w-20"
          size="small"
          :placeholder="$t('product.thingModel.enumValue')"
        />
        <Input
          v-model:value="item.description"
          class="flex-1"
          size="small"
          :placeholder="$t('product.thingModel.enumDesc')"
        />
        <Button
          danger
          size="small"
          type="text"
          @click="removeEnumItem(index as number)"
        >
          {{ $t('common.delete') }}
        </Button>
      </div>
      <Button class="w-full" dashed size="small" @click="addEnumItem">
        {{ $t('product.thingModel.addEnumItem') }}
      </Button>
    </div>
  </div>

  <!-- bool：固定 0/1 两行说明 -->
  <div v-else-if="param.dataType === 'bool'" class="mt-2 space-y-1.5">
    <div class="flex items-center gap-1.5">
      <span class="w-5 shrink-0 text-center font-mono text-xs">0</span>
      <Input
        v-model:value="param.enumItems[0].description"
        size="small"
        :placeholder="$t('product.thingModel.bool0Desc')"
      />
    </div>
    <div class="flex items-center gap-1.5">
      <span class="w-5 shrink-0 text-center font-mono text-xs">1</span>
      <Input
        v-model:value="param.enumItems[1].description"
        size="small"
        :placeholder="$t('product.thingModel.bool1Desc')"
      />
    </div>
  </div>

  <!-- struct：子字段列表（JSON 对象字段，递归支持嵌套） -->
  <div v-else-if="param.dataType === 'struct'" class="mt-2">
    <div class="text-muted-foreground mb-1 text-xs">
      {{ $t('product.thingModel.structParams') }}
    </div>
    <div class="space-y-1.5">
      <div
        v-for="(sub, index) in param.params"
        :key="index"
        class="bg-accent/30 rounded-md p-2"
      >
        <div class="flex items-center gap-1.5">
          <Input
            v-model:value="sub.name"
            size="small"
            class="flex-1"
            :placeholder="$t('product.thingModel.paramName')"
          />
          <Input
            v-model:value="sub.identifier"
            size="small"
            class="flex-1"
            :placeholder="$t('product.thingModel.paramIdentifier')"
          />
          <Select
            v-model:value="sub.dataType"
            size="small"
            class="w-24"
            :options="DATA_TYPE_OPTIONS"
            :placeholder="$t('product.thingModel.dataType')"
          />
          <Button
            danger
            size="small"
            type="text"
            @click="removeSubParam(index as number)"
          >
            {{ $t('common.delete') }}
          </Button>
        </div>
        <ParamFields :param="sub" />
      </div>
      <Button class="w-full" dashed size="small" @click="addSubParam">
        {{ $t('product.thingModel.addParam') }}
      </Button>
    </div>
  </div>
</template>
