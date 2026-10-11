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
package com.materin.tech.component.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.spi.ThingModelLookup;
import com.materin.tech.component.product.entity.ThingModel;
import com.materin.tech.component.product.mapper.ThingModelMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 跨组件 SPI 实现：查询产品已发布物模型（设备→时序桥接的列定义来源）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThingModelLookupImpl implements ThingModelLookup {

    private static final int STATUS_PUBLISHED = 1;
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {
    };

    private final ThingModelMapper thingModelMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ThingModelDigest> findPublishedByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        ThingModel model = thingModelMapper.selectOneByQuery(QueryWrapper.create()
                .eq("product_id", productId)
                .eq("status", STATUS_PUBLISHED)
                .orderBy("id", false)
                .limit(1));
        if (model == null) {
            return Optional.empty();
        }
        return Optional.of(new ThingModelDigest(productId,
                readList(model.getPropertiesJson()),
                readList(model.getMethodsJson()),
                readList(model.getEventsJson())));
    }

    private List<Map<String, Object>> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            log.warn("物模型 JSON 解析失败（按空处理）: {}", e.getMessage());
            return List.of();
        }
    }
}
