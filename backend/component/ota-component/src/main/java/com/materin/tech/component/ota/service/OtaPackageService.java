package com.materin.tech.component.ota.service;

import com.materin.tech.component.ota.entity.OtaPackage;
import com.materin.tech.component.ota.mapper.OtaPackageMapper;
import com.materin.tech.component.storage.StorageClient;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** OTA 固件包服务：文件经 StorageClient 中间件落对象存储，表只存元数据。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtaPackageService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd/HHmmss");

    private final OtaPackageMapper mapper;
    private final ObjectProvider<StorageClient> storage;
    private final com.materin.tech.component.storage.StorageProperties storageProperties;

    public Page<OtaPackage> list(long page, long pageSize, Long productId, String name) {
        QueryWrapper qw = QueryWrapper.create();
        if (productId != null) {
            qw.eq("product_id", productId);
        }
        if (name != null && !name.isBlank()) {
            qw.like("name", name);
        }
        return mapper.paginate(page, pageSize, qw.orderBy("id", false));
    }

    public OtaPackage requireById(Long id) {
        OtaPackage p = mapper.selectOneById(id);
        if (p == null) {
            throw new com.materin.tech.common.exception.BizException(404, "固件包不存在: " + id);
        }
        return p;
    }

    /**
     * 上传固件包：流式计算摘要 -> StorageClient.putObject -> 元数据落库。
     * storage 中间件不可用时抛错（上传是强存储依赖，不做静默降级）。
     */
    public OtaPackage upload(MultipartFile file, OtaPackage meta) {
        StorageClient client = storage.getIfAvailable();
        if (client == null) {
            throw new com.materin.tech.common.exception.BizException(503, "存储中间件不可用");
        }
        String signMethod = meta.getSignMethod() == null ? "md5" : meta.getSignMethod();
        String key = "ota/" + meta.getProductId() + "/" + LocalDateTime.now().format(TS)
                + "-" + UUID.randomUUID().toString().substring(0, 8) + "-"
                + file.getOriginalFilename();
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance(signMethod.toUpperCase());
            byte[] buf = new byte[8192];
            long size = 0;
            int n;
            try (InputStream digestIn = file.getInputStream()) {
                while ((n = digestIn.read(buf)) > 0) {
                    digest.update(buf, 0, n);
                    size += n;
                }
            }
            client.putObject(storageProperties.getBucket(), key, in, size,
                    file.getContentType());
            meta.setSize(size);
            meta.setSignMethod(signMethod);
            meta.setSignValue(HexFormat.of().formatHex(digest.digest()));
            meta.setStorageKey(key);
            meta.setContentType(file.getContentType());
            if (meta.getStatus() == null) {
                meta.setStatus(1);
            }
            mapper.insert(meta);
            return meta;
        } catch (com.materin.tech.common.exception.BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("固件包上传失败: {}", e.getMessage());
            throw new com.materin.tech.common.exception.BizException(500, "固件包上传失败: " + e.getMessage());
        }
    }

    /** 设备下载地址（预签名，短期有效）。 */
    public String downloadUrl(Long packageId, int expireSeconds) {
        OtaPackage p = requireById(packageId);
        StorageClient client = storage.getIfAvailable();
        if (client == null) {
            throw new com.materin.tech.common.exception.BizException(503, "存储中间件不可用");
        }
        return client.presignedGetUrl(storageProperties.getBucket(), p.getStorageKey(), expireSeconds);
    }

    public void delete(Long id) {
        OtaPackage p = requireById(id);
        StorageClient client = storage.getIfAvailable();
        if (client != null) {
            try {
                client.deleteObject(storageProperties.getBucket(), p.getStorageKey());
            } catch (Exception e) {
                log.warn("对象存储删除失败（记录仍删除）: {}", e.getMessage());
            }
        }
        mapper.deleteById(id);
    }

    public List<OtaPackage> listPublishedByProduct(Long productId) {
        return mapper.selectListByQuery(QueryWrapper.create()
                .eq("product_id", productId).eq("status", 1).orderBy("id", false));
    }
}
