package com.materin.tech.component.product.controller;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.core.R;
import com.materin.tech.component.product.dto.ProductDtos.ProductItem;
import com.materin.tech.component.product.dto.ProductDtos.ProductUpsertRequest;
import com.materin.tech.component.product.service.ProductService;
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

import java.util.List;
import java.util.Map;

/** 产品管理接口：/api/v1/product。 */
@Tag(name = "产品管理")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "分页查询产品", description = "权限：当前未接入登录态（规划中）")
    @GetMapping("/list")
    public R<PageResult<ProductItem>> list(@Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") long page,
                                           @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") long pageSize,
                                           @Parameter(description = "名称（模糊匹配）") @RequestParam(required = false) String name,
                                           @Parameter(description = "产品标识") @RequestParam(required = false) String productKey,
                                           @Parameter(description = "产品来源 standard/custom") @RequestParam(required = false) String source,
                                           @Parameter(description = "状态：1-启用 0-禁用") @RequestParam(required = false) Integer status,
                                           @Parameter(description = "创建时间起") @RequestParam(required = false) String startTime,
                                           @Parameter(description = "创建时间止") @RequestParam(required = false) String endTime) {
        return R.ok(productService.list(page, pageSize, name, productKey, source, status, startTime, endTime));
    }

    @Operation(summary = "创建产品（productKey 自动生成）", description = "权限：当前未接入登录态（规划中）")
    @PostMapping
    public R<ProductItem> create(@RequestBody ProductUpsertRequest request) {
        return R.ok(productService.create(request));
    }

    @Operation(summary = "批量导入产品", description = "权限：当前未接入登录态（规划中）")
    @PostMapping("/import")
    public R<Map<String, Integer>> imports(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault(
                "items", List.of());
        String source = String.valueOf(body.getOrDefault("source", "custom"));
        return R.ok(productService.importProducts(source, items));
    }

    @Operation(summary = "更新/删除产品", description = "权限：当前未接入登录态（规划中）")
    @PutMapping("/{id}")
    public R<ProductItem> update(@Parameter(description = "资源 ID") @PathVariable Long id, @RequestBody ProductUpsertRequest request) {
        return R.ok(productService.update(id, request));
    }

    @Operation(summary = "更新/删除产品", description = "权限：当前未接入登录态（规划中）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "资源 ID") @PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }
}
