package com.materin.tech.system.openapi.service;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.openapi.dto.OpenApiDtos.AppCreatedItem;
import com.materin.tech.system.openapi.dto.OpenApiDtos.AppItem;
import com.materin.tech.system.openapi.dto.OpenApiDtos.AppUpsertRequest;
import com.materin.tech.system.openapi.entity.OpenApp;
import com.materin.tech.system.openapi.mapper.OpenApiMapper;
import com.materin.tech.system.openapi.mapper.OpenAppApiMapper;
import com.materin.tech.system.openapi.mapper.OpenAppMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * 开发者应用管理：AK/SK 生成与生命周期。
 * SK 使用随机 32 字节，仅创建响应返回完整值，后续接口不再返回。
 */
@Service
public class OpenAppService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OpenAppMapper appMapper;
    private final OpenAppApiMapper authMapper;
    private final OpenApiMapper apiMapper;
    private final SecureRandom random = new SecureRandom();

    public OpenAppService(OpenAppMapper appMapper, OpenAppApiMapper authMapper, OpenApiMapper apiMapper) {
        this.appMapper = appMapper;
        this.authMapper = authMapper;
        this.apiMapper = apiMapper;
    }

    public PageResult<AppItem> list(long page, long pageSize, String name, Integer status) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StringUtils.hasText(name)) {
            wrapper.like("name", "%" + name + "%");
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        Page<OpenApp> result = appMapper.paginate(Page.of(page, pageSize), wrapper);
        List<AppItem> items = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(items, result.getTotalRow());
    }

    /** 创建应用：AK = "mk" + 16 hex；SK = 32 bytes hex（仅本次响应返回完整值）。 */
    public AppCreatedItem create(AppUpsertRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw BizException.badRequest("应用名不能为空");
        }
        OpenApp app = new OpenApp();
        apply(app, request);
        app.setAppKey("mk" + randomHex(8));
        app.setAppSecret(randomHex(32));
        app.setStatus(request.status() == null ? 1 : request.status());
        appMapper.insert(app);
        return new AppCreatedItem(app.getId(), app.getName(), app.getAppKey(), app.getAppSecret(),
                app.getStatus(), app.getRemark(), app.getCreateTime() == null ? null : TS.format(app.getCreateTime()));
    }

    public AppItem update(Long id, AppUpsertRequest request) {
        OpenApp app = requireApp(id);
        apply(app, request);
        appMapper.update(app);
        return toItem(app);
    }

    /** 重置 SK（旧 SK 立即失效）。 */
    public AppCreatedItem resetSecret(Long id) {
        OpenApp app = requireApp(id);
        app.setAppSecret(randomHex(32));
        appMapper.update(app);
        return new AppCreatedItem(app.getId(), app.getName(), app.getAppKey(), app.getAppSecret(),
                app.getStatus(), app.getRemark(), app.getCreateTime() == null ? null : TS.format(app.getCreateTime()));
    }

    public void delete(Long id) {
        requireApp(id);
        appMapper.deleteById(id);
        authMapper.deleteByQuery(QueryWrapper.create().eq("app_id", id));
    }

    /** 接口授权：全量替换该应用的授权清单。 */
    public void authApis(Long appId, List<Long> apiIds) {
        requireApp(appId);
        authMapper.deleteByQuery(QueryWrapper.create().eq("app_id", appId));
        if (apiIds != null) {
            for (Long apiId : apiIds) {
                authMapper.insert(new com.materin.tech.system.openapi.entity.OpenAppApi(appId, apiId));
            }
        }
    }

    /** 批量授权：增量追加，已授权的自动跳过，返回本次新增数量。 */
    public int grantApis(Long appId, List<Long> apiIds) {
        requireApp(appId);
        java.util.Set<Long> existing = new java.util.HashSet<>(authorizedApiIds(appId));
        int added = 0;
        for (Long apiId : apiIds) {
            if (apiId != null && existing.add(apiId)) {
                authMapper.insert(new com.materin.tech.system.openapi.entity.OpenAppApi(appId, apiId));
                added++;
            }
        }
        return added;
    }

    /** 批量取消授权：移除指定接口的授权，返回本次移除数量。 */
    public int revokeApis(Long appId, List<Long> apiIds) {
        requireApp(appId);
        int removed = 0;
        for (Long apiId : apiIds) {
            if (apiId == null) {
                continue;
            }
            removed += authMapper.deleteByQuery(QueryWrapper.create()
                    .eq("app_id", appId).eq("api_id", apiId));
        }
        return removed;
    }

    public List<Long> authorizedApiIds(Long appId) {
        return authMapper.selectListByQuery(QueryWrapper.create().eq("app_id", appId))
                .stream().map(com.materin.tech.system.openapi.entity.OpenAppApi::getApiId).toList();
    }

    /** 应用完整凭证（管理面查看）。 */
    public AppCreatedItem getCredentials(Long id) {
        OpenApp app = requireApp(id);
        return new AppCreatedItem(app.getId(), app.getName(), app.getAppKey(), app.getAppSecret(),
                app.getStatus(), app.getRemark(), app.getCreateTime() == null ? null : TS.format(app.getCreateTime()));
    }

    public boolean existsByAppKey(String appKey) {
        return appMapper.selectCountByQuery(QueryWrapper.create()
                .eq("app_key", appKey).eq("status", 1)) > 0;
    }

    /** AK/SK 校验（EMQX-2 认证回调使用）。 */
    public boolean matches(String appKey, String appSecret) {
        OpenApp app = appMapper.selectOneByQuery(QueryWrapper.create().eq("app_key", appKey));
        return app != null && app.getStatus() == 1 && app.getAppSecret() != null
                && app.getAppSecret().equals(appSecret);
    }

    private OpenApp requireApp(Long id) {
        OpenApp app = appMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
        if (app == null) {
            throw BizException.notFound("应用不存在: " + id);
        }
        return app;
    }

    private void apply(OpenApp app, AppUpsertRequest request) {
        if (request.name() != null) {
            app.setName(request.name());
        }
        if (request.status() != null) {
            app.setStatus(request.status());
        }
        if (request.remark() != null) {
            app.setRemark(request.remark());
        }
    }

    private AppItem toItem(OpenApp app) {
        return new AppItem(app.getId(), app.getName(), app.getAppKey(), app.getStatus(),
                app.getRemark(), app.getCreateTime() == null ? null : TS.format(app.getCreateTime()));
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
