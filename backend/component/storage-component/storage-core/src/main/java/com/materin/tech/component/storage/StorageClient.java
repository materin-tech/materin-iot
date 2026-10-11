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
package com.materin.tech.component.storage;

import java.io.InputStream;

/**
 * 对象存储统一抽象（storage-core，厂商无关）：
 * MinIO / 华为 OBS / 阿里云 OSS 等实现均实现本接口，上层（OTA 等）只面向接口。
 * 实现方必须兜底异常语义：网络类故障抛运行时异常，由调用方决定降级策略。
 */
public interface StorageClient {

    /** 上传对象（key 含路径前缀），返回对象标识（key）。 */
    String putObject(String bucket, String key, InputStream in, long size, String contentType);

    /** 获取对象流（调用方负责关闭）。 */
    InputStream getObject(String bucket, String key);

    /** 删除对象。 */
    void deleteObject(String bucket, String key);

    /** 对象元信息。 */
    StorageStat statObject(String bucket, String key);

    /**
     * 预签名下载 URL（设备侧固件下载通道，过期自动失效）。
     * 不支持预签名的实现可返回带凭证的内网直链（调用方不得外泄）。
     */
    String presignedGetUrl(String bucket, String key, int expireSeconds);

    /** 对象元信息。 */
    record StorageStat(long size, String etag, String contentType) {
    }
}
