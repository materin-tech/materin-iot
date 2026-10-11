/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace ProductApi {
  export interface PageResult<T> {
    items: T[];
    total: number;
  }

  /** 产品来源：标准产品 / 自定义产品 */
  export type ProductSource = 'custom' | 'standard';

  export interface Product {
    [key: string]: any;
    id: string;
    name: string;
    /** 产品唯一标识，设备连接时使用 */
    productKey: string;
    /** 品类，如温湿度传感器 */
    category: string;
    /** 接入协议 */
    protocol: string;
    /** 0-开发中 1-已发布 */
    status: 0 | 1;
    /** 已接入设备数 */
    deviceCount: number;
    source: ProductSource;
    remark?: string;
    createTime: string;
  }
}

/**
 * 获取产品列表数据（分页）
 * @param params 分页与筛选参数，source 用于区分标准/自定义产品
 */
async function getProductList(params: Recordable<any>) {
  return requestClient.get<ProductApi.PageResult<ProductApi.Product>>(
    '/product/list',
    { params },
  );
}

/**
 * 创建产品
 * @param data 产品数据
 */
async function createProduct(data: Omit<ProductApi.Product, 'id'>) {
  return requestClient.post('/product', data);
}

/**
 * 更新产品
 * @param id 产品 ID
 * @param data 产品数据
 */
async function updateProduct(
  id: string,
  data: Omit<ProductApi.Product, 'id'>,
) {
  return requestClient.put(`/product/${id}`, data);
}

/**
 * 删除产品
 * @param id 产品 ID
 */
async function deleteProduct(id: string) {
  return requestClient.delete(`/product/${id}`);
}

/**
 * 批量导入产品
 * @param data { source, items }
 */
async function importProducts(data: {
  items: any[];
  source: ProductApi.ProductSource;
}) {
  return requestClient.post<{
    failed: number;
    imported: number;
  }>('/product/import', data);
}

export {
  createProduct,
  deleteProduct,
  getProductList,
  importProducts,
  updateProduct,
};
