package com.materin.tech.system.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 开发者中心 DTO。 */
public final class OpenApiDtos {

    private OpenApiDtos() {
    }

    @Schema(description = "开发者应用列表项")
    public record AppItem(
            @Schema(description = "应用 ID") Long id,
            @Schema(description = "应用名") String name,
            @Schema(description = "访问密钥 AK（公开）") String appKey,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "备注") String remark,
            @Schema(description = "创建时间") String createTime) {
    }

    @Schema(description = "应用创建/重置 SK 响应（SK 仅本次返回完整值）")
    public record AppCreatedItem(
            @Schema(description = "应用 ID") Long id,
            @Schema(description = "应用名") String name,
            @Schema(description = "访问密钥 AK") String appKey,
            @Schema(description = "私密密钥 SK（请妥善保管）") String appSecret,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "备注") String remark,
            @Schema(description = "创建时间") String createTime) {
    }

    @Schema(description = "应用创建/更新请求")
    public record AppUpsertRequest(
            @Schema(description = "应用名") String name,
            @Schema(description = "状态：1-启用 0-禁用") Integer status,
            @Schema(description = "备注") String remark) {
    }

    @Schema(description = "接口清单项")
    public record ApiItem(
            @Schema(description = "接口 ID") Long id,
            @Schema(description = "HTTP 方法") String method,
            @Schema(description = "接口路径") String path,
            @Schema(description = "接口说明") String summary,
            @Schema(description = "接口分组（swagger tag）") String tag) {
    }

    @Schema(description = "接口授权请求")
    public record ApiAuthRequest(
            @Schema(description = "授权接口 ID 集合") List<Long> apiIds) {
    }
}
