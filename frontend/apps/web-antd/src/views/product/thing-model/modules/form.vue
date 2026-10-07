<script lang="ts" setup>
import type { ThingModelApi } from '#/api/product/thing-model';

import { computed, reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, TabPane, Tabs } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createThingModel,
  updateThingModel,
} from '#/api/product/thing-model';
import { $t } from '#/locales';

import {
  THING_MODEL_SECTIONS,
  useFormSchema,
  type SectionKey,
} from '../data';
import ItemForm from './item-form.vue';

const emits = defineEmits(['success']);

const [Form, formApi] = useVbenForm({
  layout: 'vertical',
  schema: useFormSchema(),
  showDefaultActions: false,
});

/** 三要素内容为本地可变列表，随表单一并提交 */
const sections = reactive<Record<SectionKey, any[]>>({
  events: [],
  methods: [],
  properties: [],
});

const activeTab = ref<SectionKey>('properties');

/** 二级悬浮表单状态 */
const itemForm = reactive({
  index: -1,
  item: null as null | Record<string, any>,
  open: false,
  section: 'properties' as SectionKey,
});

function setSection(key: SectionKey, items: unknown[] | undefined) {
  sections[key] = ((items ?? []) as ThingModelApi.ParamItem[]).map((item) => ({
    ...item,
  }));
}

function openAdd(section: SectionKey) {
  Object.assign(itemForm, {
    index: -1,
    item: null,
    open: true,
    section,
  });
}

function openEdit(section: SectionKey, index: number) {
  Object.assign(itemForm, {
    index,
    item: sections[section][index] ?? null,
    open: true,
    section,
  });
}

function removeItem(section: SectionKey, index: number) {
  sections[section] = sections[section].filter((_, i) => i !== index);
}

/** 二级表单保存：新增追加、编辑替换 */
function onSaveItem(item: Record<string, any>) {
  if (itemForm.index >= 0) {
    sections[itemForm.section] = sections[itemForm.section].map((old, i) =>
      i === itemForm.index ? item : old,
    );
  } else {
    sections[itemForm.section] = [...sections[itemForm.section], item];
  }
}

/** 条目摘要行显示的元信息 */
function itemMeta(
  section: SectionKey,
  item: ThingModelApi.ParamItem,
): string {
  if (section === 'properties') {
    const p = item as ThingModelApi.PropertyItem;
    let constraint = '';
    if (['double', 'int'].includes(p.dataType ?? '')) {
      constraint =
        p.min !== undefined || p.max !== undefined
          ? `${p.min ?? '-'}~${p.max ?? '-'}${p.unit ?? ''}`
          : '';
    } else if (p.dataType === 'enum') {
      constraint = `${$t('product.thingModel.enumItems')} ${p.enumItems?.length ?? 0}`;
    } else if (p.dataType === 'bool') {
      constraint = `0/1`;
    } else if (p.dataType === 'string') {
      constraint = p.maxLength ? `≤${p.maxLength}` : '';
    } else if (p.dataType === 'struct') {
      constraint = `${$t('product.thingModel.structParams')} ${p.params?.length ?? 0}`;
    }
    return (
      [
        p.category,
        p.dataType,
        p.accessMode === 'r'
          ? $t('product.thingModel.modeR')
          : $t('product.thingModel.modeRw'),
      ]
        .filter(Boolean)
        .join(' · ') + (constraint ? ` · ${constraint}` : '')
    );
  }
  if (section === 'methods') {
    const m = item as ThingModelApi.MethodItem;
    return [
      m.callType === 'async'
        ? $t('product.thingModel.callAsync')
        : $t('product.thingModel.callSync'),
      `${$t('product.thingModel.inputParams')} ${m.inputParams?.length ?? 0}`,
      m.outputParam
        ? `${$t('product.thingModel.outputParam')} 1`
        : $t('product.thingModel.noOutputParam'),
    ].join(' · ');
  }
  const e = item as ThingModelApi.EventItem;
  return [
    $t(
      `product.thingModel.level${
        { error: 'Error', info: 'Info', warn: 'Warn' }[e.level ?? 'info']
      }`,
    ),
    `${$t('product.thingModel.outputParams')} ${e.outputParams?.length ?? 0}`,
  ].join(' · ');
}

function itemValid(item: any): boolean {
  return Boolean(item?.name?.trim() && item?.identifier?.trim());
}

const id = ref();
const [Modal, modalApi] = useVbenModal<null | Partial<ThingModelApi.ThingModel>>({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    // 三要素条目校验：名称/标识符必填
    for (const section of THING_MODEL_SECTIONS) {
      const invalid = sections[section.itemsKey].some(
        (item) => !itemValid(item),
      );
      if (invalid) {
        message.error($t('product.thingModel.itemInvalid', [section.label]));
        activeTab.value = section.itemsKey;
        return;
      }
    }
    const values = await formApi.getValues();
    const payload = {
      ...values,
      events: sections.events,
      methods: sections.methods,
      properties: sections.properties,
    };
    modalApi.lock();
    (id.value ? updateThingModel(id.value, payload) : createThingModel(payload))
      .then(() => {
        emits('success');
        modalApi.close();
      })
      .catch(() => {
        modalApi.lock(false);
      });
  },

  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData();
      formApi.reset();
      activeTab.value = 'properties';

      if (data?.id) {
        id.value = data.id;
        formApi.setValues(data);
        setSection('events', data.events);
        setSection('methods', data.methods);
        setSection('properties', data.properties);
      } else {
        id.value = undefined;
        setSection('events', []);
        setSection('methods', []);
        setSection('properties', []);
      }
    }
  },
});

defineExpose({ modalApi });

const getModalTitle = computed(() => {
  const name = $t('page.product.thingModel');
  return id.value ? $t('common.edit', name) : $t('common.create', name);
});
</script>

<template>
  <Modal class="w-[760px]" :title="getModalTitle">
    <Form />
    <Tabs v-model:active-key="activeTab" class="mt-2">
      <TabPane
        v-for="section in THING_MODEL_SECTIONS"
        :key="section.itemsKey"
      >
        <template #tab>
          <span :class="section.icon" class="mr-1 inline-block size-3.5 align-[-2px]"></span>
          {{ section.label }}
          <span class="text-muted-foreground ml-1">
            {{ sections[section.itemsKey].length }}
          </span>
        </template>

        <div class="min-h-40 space-y-2">
          <div
            v-for="(item, index) in sections[section.itemsKey]"
            :key="index"
            class="hover:border-primary/40 flex items-center gap-3 rounded-lg border p-3 transition-all"
          >
            <!-- 摘要 -->
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="text-foreground truncate text-sm font-medium">
                  {{ item.name }}
                </span>
                <span class="text-muted-foreground font-mono text-xs">
                  {{ item.identifier }}
                </span>
              </div>
              <div class="text-muted-foreground mt-1 text-xs">
                {{ itemMeta(section.itemsKey, item) }}
              </div>
            </div>
            <!-- 操作 -->
            <div class="flex shrink-0 items-center gap-1">
              <Button size="small" type="text" @click="openEdit(section.itemsKey, index)">
                {{ $t('common.edit') }}
              </Button>
              <Button danger size="small" type="text" @click="removeItem(section.itemsKey, index)">
                {{ $t('common.delete') }}
              </Button>
            </div>
          </div>

          <div
            v-if="sections[section.itemsKey].length === 0"
            class="text-muted-foreground py-6 text-center text-xs"
          >
            {{ $t('product.thingModel.emptySection') }}
          </div>
          <Button
            class="w-full"
            dashed
            @click="openAdd(section.itemsKey)"
          >
            + {{ $t('product.thingModel.addItem') }}
          </Button>
        </div>
      </TabPane>
    </Tabs>

    <!-- 二级悬浮表单：按要素动态出字段 -->
    <ItemForm
      v-model:open="itemForm.open"
      :item="itemForm.item"
      :section="itemForm.section"
      @save="onSaveItem"
    />
  </Modal>
</template>
