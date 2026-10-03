package com.materin.tech.system.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.system.openapi.dto.OpenApiDtos.ApiItem;
import com.materin.tech.system.openapi.entity.OpenApi;
import com.materin.tech.system.openapi.mapper.OpenApiMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** 平台接口清单：从 OpenAPI(/v3/api-docs) 同步入库，供接口授权页展示与勾选。 */
@Service
public class OpenApiCatalogService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OpenApiMapper apiMapper;
    private final com.materin.tech.system.openapi.mapper.OpenAppApiMapper authMapper;
    private final ObjectMapper objectMapper;

    public OpenApiCatalogService(OpenApiMapper apiMapper,
                                 com.materin.tech.system.openapi.mapper.OpenAppApiMapper authMapper,
                                 ObjectMapper objectMapper) {
        this.apiMapper = apiMapper;
        this.authMapper = authMapper;
        this.objectMapper = objectMapper;
    }

    public PageResult<ApiItem> list(long page, long pageSize, String keyword, String method,
                                    Long appId, String authFilter) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword + "%";
            wrapper.and(new QueryColumn("path").like(like)
                    .or(new QueryColumn("summary").like(like)));
        }
        if (StringUtils.hasText(method)) {
            wrapper.eq("method", method);
        }
        // 已授权/未授权筛选（DB 层过滤，配合分页）
        if (appId != null && ("authorized".equals(authFilter) || "unauthorized".equals(authFilter))) {
            List<Long> authorizedIds = authMapper.selectListByQuery(
                            QueryWrapper.create().eq("app_id", appId))
                    .stream().map(a -> a.getApiId()).toList();
            if (authorizedIds.isEmpty() && "authorized".equals(authFilter)) {
                return new PageResult<>(List.of(), 0);
            }
            if ("authorized".equals(authFilter)) {
                wrapper.and(new QueryColumn("id").in(authorizedIds));
            } else {
                wrapper.and(new QueryColumn("id").notIn(authorizedIds));
            }
        }
        Page<OpenApi> result = apiMapper.paginate(Page.of(page, pageSize), wrapper);
        List<ApiItem> items = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(items, result.getTotalRow());
    }

    /** 从 OpenAPI JSON 同步接口清单（method+path 幂等去重，返回新增条数）。 */
    public int syncFrom(String openapiJsonUrl) {
        String body = fetch(openapiJsonUrl);
        List<OpenApi> toInsert = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode paths = root.path("paths");
            paths.fields().forEachRemaining(pathEntry -> {
                String path = pathEntry.getKey();
                pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                    String method = methodEntry.getKey().toUpperCase();
                    if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) {
                        return;
                    }
                    JsonNode op = methodEntry.getValue();
                    OpenApi api = new OpenApi();
                    api.setMethod(method);
                    api.setPath(path);
                    api.setSummary(op.path("summary").asText(""));
                    api.setTag(op.path("tags").isEmpty() ? "默认"
                            : op.path("tags").get(0).asText("默认"));
                    toInsert.add(api);
                });
            });
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "OpenAPI 解析失败: " + e.getMessage());
        }
        if (toInsert.isEmpty()) {
            return 0;
        }
        java.util.Map<String, OpenApi> existing = new java.util.HashMap<>();
        apiMapper.selectAll().forEach(a -> existing.put(a.getMethod() + " " + a.getPath(), a));
        int changed = 0;
        for (OpenApi api : toInsert) {
            OpenApi old = existing.get(api.getMethod() + " " + api.getPath());
            if (old == null) {
                apiMapper.insert(api);
                changed++;
            } else if (!api.getSummary().equals(old.getSummary())
                    || !api.getTag().equals(old.getTag())) {
                // 说明/分组随注解演进，同步刷新
                old.setSummary(api.getSummary());
                old.setTag(api.getTag());
                apiMapper.update(old);
                changed++;
            }
        }
        return changed;
    }

    private String fetch(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BizException(500, "拉取 OpenAPI 失败: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "拉取 OpenAPI 失败: " + e.getMessage());
        }
    }

    private ApiItem toItem(OpenApi api) {
        return new ApiItem(api.getId(), api.getMethod(), api.getPath(),
                api.getSummary(), api.getTag());
    }

    /** 单接口详情：实时解析 OpenAPI，返回完整参数/请求体/响应说明（knife4j 式）。 */
    public java.util.Map<String, Object> detail(String method, String path) {
        String body = fetch("http://localhost:8080/v3/api-docs");
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode op = root.path("paths").path(path).path(method.toLowerCase());
            if (op.isMissingNode()) {
                throw BizException.notFound("接口不存在: " + method + " " + path);
            }
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("method", method.toUpperCase());
            result.put("path", path);
            result.put("summary", op.path("summary").asText(""));
            result.put("description", op.path("description").asText(""));
            result.put("tag", op.path("tags").isEmpty() ? "" : op.path("tags").get(0).asText(""));
            // 解析 $ref schema 的字段说明
            java.util.function.UnaryOperator<JsonNode> resolve = node -> {
                if (node.has("$ref")) {
                    String ref = node.get("$ref").asText();
                    return root.at(ref.substring(1));
                }
                return node;
            };
            List<java.util.Map<String, Object>> parameters = new ArrayList<>();
            for (JsonNode param : op.path("parameters")) {
                JsonNode schema = resolve.apply(param.path("schema"));
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("name", param.path("name").asText());
                item.put("in", param.path("in").asText());
                item.put("description", param.path("description").asText());
                item.put("required", param.path("required").asBoolean(false));
                item.put("type", schema.path("type").asText("string"));
                parameters.add(item);
            }
            result.put("parameters", parameters);
            java.util.Map<String, Object> requestBody = null;
            JsonNode rb = op.path("requestBody");
            if (!rb.isMissingNode()) {
                JsonNode schema = resolve.apply(rb.path("content").path("application/json").path("schema"));
                List<java.util.Map<String, Object>> fields = new ArrayList<>();
                JsonNode props = schema.path("properties");
                props.fields().forEachRemaining(e -> {
                    JsonNode v = e.getValue();
                    java.util.Map<String, Object> f = new java.util.HashMap<>();
                    f.put("name", e.getKey());
                    f.put("type", v.path("type").asText(v.has("$ref") || v.path("type").asText().isEmpty() ? "object" : v.path("type").asText()));
                    f.put("description", v.path("description").asText());
                    fields.add(f);
                });
                java.util.List<String> requiredList = new java.util.ArrayList<>();
                schema.path("required").forEach(n -> requiredList.add(n.asText()));
                requestBody = new java.util.HashMap<>();
                requestBody.put("description", rb.path("description").asText(""));
                requestBody.put("fields", fields);
                requestBody.put("required", requiredList);
            }
            result.put("requestBody", requestBody);
            List<java.util.Map<String, Object>> responses = new ArrayList<>();
            op.path("responses").fields().forEachRemaining(e -> {
                java.util.Map<String, Object> r = new java.util.HashMap<>();
                r.put("code", e.getKey());
                r.put("description", e.getValue().path("description").asText());
                // 解析响应体 schema：$ref 解引用，data 泛型载荷标注嵌套类型
                JsonNode schemaNode = resolve.apply(
                        e.getValue().path("content").path("*/*").path("schema"));
                List<java.util.Map<String, Object>> fields = new ArrayList<>();
                JsonNode props = schemaNode.path("properties");
                props.fields().forEachRemaining(pe -> {
                    JsonNode v = pe.getValue();
                    JsonNode resolved = resolve.apply(v);
                    java.util.Map<String, Object> f = new java.util.HashMap<>();
                    f.put("name", pe.getKey());
                    String typeName = v.has("$ref") || resolved.path("type").asText().isEmpty()
                            ? "object" : resolved.path("type").asText();
                    f.put("type", typeName);
                    f.put("description", resolved.path("description").asText());
                    fields.add(f);
                    // data 载荷：展开一层嵌套字段（data.xxx），完整展示出参结构
                    if ("data".equals(pe.getKey()) && resolved.has("properties")) {
                        JsonNode dataProps = resolved.path("properties");
                        dataProps.fields().forEachRemaining(de -> {
                            JsonNode dv = resolve.apply(de.getValue());
                            java.util.Map<String, Object> df = new java.util.HashMap<>();
                            df.put("name", "data." + de.getKey());
                            df.put("in", "body");
                            String dt = dv.path("type").asText();
                            df.put("type", dt.isEmpty() || dv.has("$ref") ? "object" : dt);
                            df.put("description", dv.path("description").asText());
                            df.put("required", false);
                            fields.add(df);
                        });
                    }
                });
                r.put("fields", fields);
                responses.add(r);
            });
            result.put("responses", responses);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "OpenAPI 解析失败: " + e.getMessage());
        }
    }
}
