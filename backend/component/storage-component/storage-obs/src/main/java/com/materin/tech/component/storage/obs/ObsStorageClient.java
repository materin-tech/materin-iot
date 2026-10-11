/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.tech.component.storage.obs;

import com.materin.tech.component.storage.StorageClient;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import com.materin.tech.component.storage.StorageProperties;
import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PutObjectResult;

/**
 * StorageClient 的华为 OBS 实现（storage-obs）。
 * 遵循与 MinIO 相同的懒初始化模式；预签名用 createSignedUrl（V4 签名）。
 */
@Slf4j
public class ObsStorageClient implements StorageClient {

    private final StorageProperties properties;
    private volatile ObsClient client;

    public ObsStorageClient(StorageProperties properties) {
        this.properties = properties;
    }

    private ObsClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    ObsConfiguration config = new ObsConfiguration();
                    config.setEndPoint(properties.getEndpoint());
                    config.setSocketTimeout(60_000);
                    client = new ObsClient(properties.getAccessKey(), properties.getSecretKey(), config);
                    log.info("华为 OBS 客户端已初始化: {}", properties.getEndpoint());
                }
            }
        }
        return client;
    }

    @Override
    public String putObject(String bucket, String key, InputStream in, long size, String contentType) {
        try (InputStream stream = in) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            if (contentType != null) {
                metadata.setContentType(contentType);
            }
            client().putObject(bucket, key, stream, metadata);
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("OBS putObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream getObject(String bucket, String key) {
        try {
            ObsObject obj = client().getObject(bucket, key);
            return obj.getObjectContent();
        } catch (Exception e) {
            throw new IllegalStateException("OBS getObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try {
            client().deleteObject(bucket, key);
        } catch (Exception e) {
            throw new IllegalStateException("OBS deleteObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public StorageStat statObject(String bucket, String key) {
        try {
            ObjectMetadata md = client().getObjectMetadata(bucket, key);
            return new StorageStat(md.getContentLength(), md.getEtag(), md.getContentType());
        } catch (Exception e) {
            throw new IllegalStateException("OBS statObject 失败: " + key + ", " + e.getMessage(), e);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String key, int expireSeconds) {
        try {
            // 华为 OBS 预签名：createSignedUrl(HttpMethodEnum, expireSeconds, bucket, key)
            return client().createSignedUrl(com.obs.services.model.HttpMethodEnum.GET,
                    bucket, key, null, expireSeconds, null, null);
        } catch (Exception e) {
            throw new IllegalStateException("OBS presignedGetUrl 失败: " + key + ", " + e.getMessage(), e);
        }
    }
}
