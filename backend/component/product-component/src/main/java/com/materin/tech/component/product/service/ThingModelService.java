package com.materin.tech.component.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.component.product.dto.ProductDtos.ThingModelItem;
import com.materin.tech.component.product.dto.ProductDtos.ThingModelUpsertRequest;
import com.materin.tech.component.product.entity.ThingModel;
import com.materin.tech.component.product.mapper.ThingModelMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 物模型管理：属性/方法/事件 JSON 存取。 */
@Service
@RequiredArgsConstructor
public class ThingModelService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {
    };

    private final ThingModelMapper thingModelMapper;
    private final ObjectMapper objectMapper;

    public PageResult<ThingModelItem> list(long page, long pageSize, String keyword,
                                           Integer status) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword + "%";
            wrapper.and(new QueryColumn("product_name").like(like)
                    .or(new QueryColumn("version").like(like)));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        Page<ThingModel> result = thingModelMapper.paginate(Page.of(page, pageSize), wrapper);
        List<ThingModelItem> items = result.getRecords().stream()
                .map(m -> toItem(m, true)).toList();
        return new PageResult<>(items, result.getTotalRow());
    }

    public ThingModelItem create(ThingModelUpsertRequest request) {
        if (request.productId() == null) {
            throw BizException.badRequest("所属产品不能为空");
        }
        ThingModel model = new ThingModel();
        model.setProductId(request.productId());
        model.setProductName(request.productName());
        model.setVersion(StringUtils.hasText(request.version()) ? request.version() : "v1.0");
        model.setStatus(request.status() == null ? 0 : request.status());
        model.setRemark(request.remark());
        writeJson(model, request);
        thingModelMapper.insert(model);
        return toItem(model, true);
    }

    public ThingModelItem update(Long id, ThingModelUpsertRequest request) {
        ThingModel model = requireModel(id);
        if (request.productId() != null) {
            model.setProductId(request.productId());
        }
        if (request.productName() != null) {
            model.setProductName(request.productName());
        }
        if (StringUtils.hasText(request.version())) {
            model.setVersion(request.version());
        }
        if (request.status() != null) {
            model.setStatus(request.status());
        }
        if (request.remark() != null) {
            model.setRemark(request.remark());
        }
        writeJson(model, request);
        thingModelMapper.update(model);
        return toItem(model, true);
    }

    public void delete(Long id) {
        requireModel(id);
        thingModelMapper.deleteById(id);
    }

    private void writeJson(ThingModel model, ThingModelUpsertRequest request) {
        try {
            model.setPropertiesJson(objectMapper.writeValueAsString(
                    request.properties() == null ? List.of() : request.properties()));
            model.setMethodsJson(objectMapper.writeValueAsString(
                    request.methods() == null ? List.of() : request.methods()));
            model.setEventsJson(objectMapper.writeValueAsString(
                    request.events() == null ? List.of() : request.events()));
        } catch (Exception e) {
            throw new BizException(500, "物模型 JSON 序列化失败");
        }
    }

    private ThingModel requireModel(Long id) {
        ThingModel model = thingModelMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
        if (model == null) {
            throw BizException.notFound("物模型不存在: " + id);
        }
        return model;
    }

    private ThingModelItem toItem(ThingModel model, boolean withContent) {
        String createTime = model.getCreateTime() == null ? null
                : model.getCreateTime().format(TS);
        return new ThingModelItem(model.getId(), model.getProductId(), model.getProductName(),
                model.getVersion(), model.getStatus(),
                readJson(model.getPropertiesJson()), readJson(model.getMethodsJson()),
                readJson(model.getEventsJson()), model.getRemark(), createTime);
    }

    private List<Map<String, Object>> readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }
}
