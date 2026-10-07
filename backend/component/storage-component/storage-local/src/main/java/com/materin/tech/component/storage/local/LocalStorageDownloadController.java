package com.materin.tech.component.storage.local;

import com.materin.tech.component.storage.StorageClient;
import com.materin.tech.component.storage.StorageProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/**
 * 本地存储设备下载端点（LocalStorageClient.presignedGetUrl 指向这里）。
 * GET /api/v1/storage/local/{bucket}/{key...}?expire=&sign=
 * 校验 HMAC 签名与有效期；路径穿越由 LocalStorageClient.resolve 防护。
 */
@Slf4j
@RestController
@RequestMapping("/storage/local")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "materin.storage", name = "type", havingValue = "LOCAL")
public class LocalStorageDownloadController {

    private final ObjectProvider<StorageClient> storage;
    private final StorageProperties properties;

    @GetMapping("/**")
    public ResponseEntity<StreamingResponseBody> download(HttpServletRequest request,
                                                @RequestParam(required = false) Long expire,
                                                @RequestParam(required = false) String sign) {
        StorageClient client = storage.getIfAvailable();
        if (client == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        // 提取 bucket/key：/storage/local 之后的部分
        String full = request.getRequestURI();
        String prefix = "/storage/local/";
        int idx = full.indexOf(prefix);
        if (idx < 0 || expire == null || sign == null) {
            return ResponseEntity.badRequest().build();
        }
        String bucketAndKey = full.substring(idx + prefix.length());
        if (bucketAndKey.isBlank() || !bucketAndKey.contains("/")) {
            return ResponseEntity.badRequest().build();
        }
        String bucket = bucketAndKey.substring(0, bucketAndKey.indexOf('/'));
        String key = bucketAndKey.substring(bucketAndKey.indexOf('/') + 1);
        // 验签 + 有效期
        String expected = LocalStorageClient.sign(
                properties.getSecretKey(), bucket, key, expire);
        if (System.currentTimeMillis() > expire || !expected.equals(sign)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            InputStream in = client.getObject(bucket, key);
            String mt = guessContentType(key);
            StreamingResponseBody body = out -> {
                try (InputStream stream = in) {
                    stream.transferTo(out);
                }
            };
            return ResponseEntity.ok()
                    .contentType(mt == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mt))
                    .body(body);
        } catch (Exception e) {
            log.warn("本地存储下载失败: bucket={}, key={}, {}", bucket, key, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private String guessContentType(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".bin")) return "application/octet-stream";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".zip")) return "application/zip";
        return null;
    }

}
