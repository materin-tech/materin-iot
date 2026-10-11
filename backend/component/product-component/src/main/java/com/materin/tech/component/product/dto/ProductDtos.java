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
package com.materin.tech.component.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** 产品 / 物模型 DTO（对齐前端契约）。 */
public final class ProductDtos {

    private ProductDtos() {
    }

    @Schema(description = "产品列表项")
    public record ProductItem(
            @Schema(description = "产品 ID") Long id,
            @Schema(description = "产品名") String name,
            @Schema(description = "产品唯一标识") String productKey,
            @Schema(description = "品类") String category,
            @Schema(description = "接入协议") String protocol,
            @Schema(description = "状态：0-开发中 1-已发布") Integer status,
            @Schema(description = "已接入设备数") Integer deviceCount,
            @Schema(description = "来源：standard/custom") String source,
            @Schema(description = "备注") String remark,
            @Schema(description = "节点形态：DIRECT/GATEWAY/CHILD") String nodeType,
            @Schema(description = "创建时间") String createTime) {
    }

    @Schema(description = "产品创建/更新请求")
    public record ProductUpsertRequest(
            @Schema(description = "产品名") String name,
            @Schema(description = "产品唯一标识（留空自动生成）") String productKey,
            @Schema(description = "品类") String category,
            @Schema(description = "接入协议") String protocol,
            @Schema(description = "状态：0-开发中 1-已发布") Integer status,
            @Schema(description = "来源：standard/custom") String source,
            @Schema(description = "备注") String remark,
            @Schema(description = "节点形态：DIRECT/GATEWAY/CHILD（缺省 DIRECT）") String nodeType) {
    }

    @Schema(description = "物模型列表项")
    public record ThingModelItem(
            @Schema(description = "物模型 ID") Long id,
            @Schema(description = "所属产品 ID") Long productId,
            @Schema(description = "产品名称") String productName,
            @Schema(description = "模型版本") String version,
            @Schema(description = "状态：0-草稿 1-已发布") Integer status,
            @Schema(description = "属性列表（含 dataType/min/max/unit 等定义）") Object properties,
            @Schema(description = "方法列表（含入参/出参定义）") Object methods,
            @Schema(description = "事件列表（含级别/出参定义）") Object events,
            @Schema(description = "备注") String remark,
            @Schema(description = "创建时间") String createTime) {
    }

    @Schema(description = "物模型创建/更新请求")
    public record ThingModelUpsertRequest(
            @Schema(description = "所属产品 ID") Long productId,
            @Schema(description = "产品名称") String productName,
            @Schema(description = "模型版本（默认 v1.0）") String version,
            @Schema(description = "状态：0-草稿 1-已发布") Integer status,
            @Schema(description = "备注") String remark,
            @Schema(description = "属性列表") List<Map<String, Object>> properties,
            @Schema(description = "方法列表") List<Map<String, Object>> methods,
            @Schema(description = "事件列表") List<Map<String, Object>> events) {
    }
}
