package com.materin.tech.system.openapi.controller;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.system.openapi.dto.OpenApiDtos.ApiItem;
import com.materin.tech.system.openapi.service.OpenApiCatalogService;
import com.materin.tech.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台接口清单接口：/api/v1/open/apis。 */
@Tag(name = "接口清单")
@RestController
@RequestMapping("/open/apis")
public class OpenApiCatalogController {

    private final OpenApiCatalogService catalogService;

    public OpenApiCatalogController(OpenApiCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Operation(summary = "分页查询接口清单", description = "权限：需登录（开发者中心管理面）")
    @GetMapping("/list")
    public R<PageResult<ApiItem>> list(@Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") long page,
                                        @Parameter(description = "每页条数") @RequestParam(defaultValue = "50") long pageSize,
                                        @Parameter(description = "关键字（模糊匹配）") @RequestParam(required = false) String keyword,
                                        @Parameter(description = "HTTP 方法") @RequestParam(required = false) String method,
                                        @Parameter(description = "应用 ID（配合 authFilter 使用）") @RequestParam(required = false) Long appId,
                                        @Parameter(description = "授权筛选：authorized-已授权 unauthorized-未授权") @RequestParam(required = false) String authFilter) {
        return R.ok(catalogService.list(page, pageSize, keyword, method, appId, authFilter));
    }

    /** 从本服务 /v3/api-docs 同步接口清单。 */
    @Operation(summary = "从 OpenAPI(/v3/api-docs) 同步接口清单", description = "权限：需登录（开发者中心管理面）")
    @PostMapping("/sync")
    public R<Integer> sync(@Parameter(description = "OpenAPI JSON 地址") @RequestParam(defaultValue = "http://localhost:8080/v3/api-docs") String url) {
        return R.ok(catalogService.syncFrom(url));
    }

    /** 单接口详情：完整参数/请求体/响应说明。 */
    @Operation(summary = "查询单接口详情（参数/请求体/响应说明）", description = "权限：需登录（开发者中心管理面）")
    @GetMapping("/detail")
    public R<java.util.Map<String, Object>> detail(
            @Parameter(description = "HTTP 方法") @RequestParam String method,
            @Parameter(description = "接口路径") @RequestParam String path) {
        return R.ok(catalogService.detail(method, path));
    }
}
