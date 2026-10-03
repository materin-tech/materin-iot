package com.materin.tech.component.product.controller;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.core.R;
import com.materin.tech.component.product.dto.ProductDtos.ThingModelItem;
import com.materin.tech.component.product.dto.ProductDtos.ThingModelUpsertRequest;
import com.materin.tech.component.product.service.ThingModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 物模型接口：/api/v1/product/thing-model。 */
@Tag(name = "物模型")
@RestController
@RequestMapping("/product/thing-model")
@RequiredArgsConstructor
public class ThingModelController {

    private final ThingModelService thingModelService;

    @Operation(summary = "分页查询物模型", description = "权限：当前未接入登录态（规划中）")
    @GetMapping("/list")
    public R<PageResult<ThingModelItem>> list(@Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") long page,
                                        @Parameter(description = "每页条数") @RequestParam(defaultValue = "12") long pageSize,
                                        @Parameter(description = "关键字（模糊匹配）") @RequestParam(required = false) String keyword,
                                        @Parameter(description = "状态：1-启用 0-禁用") @RequestParam(required = false) Integer status) {
        return R.ok(thingModelService.list(page, pageSize, keyword, status));
    }

    @Operation(summary = "创建物模型", description = "权限：当前未接入登录态（规划中）")
    @PostMapping
    public R<ThingModelItem> create(@RequestBody ThingModelUpsertRequest request) {
        return R.ok(thingModelService.create(request));
    }

    @Operation(summary = "更新/删除物模型", description = "权限：当前未接入登录态（规划中）")
    @PutMapping("/{id}")
    public R<ThingModelItem> update(@Parameter(description = "资源 ID") @PathVariable Long id,
                                    @RequestBody ThingModelUpsertRequest request) {
        return R.ok(thingModelService.update(id, request));
    }

    @Operation(summary = "更新/删除物模型", description = "权限：当前未接入登录态（规划中）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        thingModelService.delete(id);
        return R.ok();
    }
}
