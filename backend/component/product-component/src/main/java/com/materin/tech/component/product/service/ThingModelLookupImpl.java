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
