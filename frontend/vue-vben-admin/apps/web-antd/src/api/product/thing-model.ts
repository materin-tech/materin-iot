import type { Recordable } from '@vben/types';

import type { ProductApi } from '#/api/product/product';

import { requestClient } from '#/api/request';

export namespace ThingModelApi {
  /** 参数（用于方法入参/返回值、事件返回结果参数、struct 子参数） */
  export interface ParamItem {
    [key: string]: any;
    name: string;
    identifier: string;
    dataType?: string;
    /** 参数说明 */
    description?: string;
    /** 数值约束 */
    max?: number;
    min?: number;
    unit?: string;
    /** enum 的取值+说明 / bool 固定 [0,1] 两项 */
    enumItems?: EnumItem[];
    /** dataType=struct 时的子字段（递归） */
    params?: ParamItem[];
  }

  /** 枚举项（enum 的取值+说明；bool 时固定 [0,1] 两项） */
  export interface EnumItem {
    description: string;
    value: number | string;
  }

  /** 属性条目 */
  export interface PropertyItem {
    [key: string]: any;
    name: string;
    identifier: string;
    /** 属性分类，如 运行状态/故障诊断 */
    category?: string;
    dataType?: string;
    /** 读写类型：r-只读 rw-读写 */
    accessMode?: 'r' | 'rw';
    max?: number;
    min?: number;
    step?: number;
    unit?: string;
    maxLength?: number;
    /** enum/bool 的取值与说明 */
    enumItems?: EnumItem[];
    /** struct 的子参数 */
    params?: ParamItem[];
    /** 字段说明 */
    remark?: string;
  }

  /** 方法条目：入参 + 返回值 */
  export interface MethodItem {
    [key: string]: any;
    name: string;
    identifier: string;
    /** 调用方式：sync-同步 async-异步 */
    callType?: 'async' | 'sync';
    inputParams: ParamItem[];
    /** 返回值（单对象，可为空表示无返回） */
    outputParam?: null | ParamItem;
    remark?: string;
  }

  /** 事件条目：返回结果参数 */
  export interface EventItem {
    [key: string]: any;
    name: string;
    identifier: string;
    /** 事件级别：info-信息 warn-警告 error-故障 */
    level?: 'error' | 'info' | 'warn';
    outputParams: ParamItem[];
    remark?: string;
  }

  /**
   * 物模型 = 某产品的一份模型定义，
   * 内含 属性(properties) / 方法(methods) / 事件(events) 三要素。
   */
  export interface ThingModel {
    [key: string]: any;
    id: string;
    /** 所属产品 ID */
    productId: string;
    /** 所属产品名称 */
    productName: string;
    /** 模型版本 */
    version: string;
    /** 0-草稿 1-已发布 */
    status: 0 | 1;
    properties: PropertyItem[];
    methods: MethodItem[];
    events: EventItem[];
    remark?: string;
    createTime: string;
  }
}

/**
 * 获取物模型列表数据（分页）
 */
async function getThingModelList(params: Recordable<any>) {
  return requestClient.get<
    ProductApi.PageResult<ThingModelApi.ThingModel>
  >('/product/thing-model/list', { params });
}

/**
 * 创建物模型
 * @param data 物模型数据
 */
async function createThingModel(data: Omit<ThingModelApi.ThingModel, 'id'>) {
  return requestClient.post('/product/thing-model', data);
}

/**
 * 更新物模型
 * @param id 物模型 ID
 * @param data 物模型数据
 */
async function updateThingModel(
  id: string,
  data: Omit<ThingModelApi.ThingModel, 'id'>,
) {
  return requestClient.put(`/product/thing-model/${id}`, data);
}

/**
 * 删除物模型
 * @param id 物模型 ID
 */
async function deleteThingModel(id: string) {
  return requestClient.delete(`/product/thing-model/${id}`);
}

export {
  createThingModel,
  deleteThingModel,
  getThingModelList,
  updateThingModel,
};
