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
package com.materin.tech.common.spi;

/**
 * 文件登记 SPI（common，业务模块与文件管理模块解耦）：
 * 任何经 StorageClient 上传的文件，业务方在上传成功后调用 register 登记，
 * 文件管理（system-file）汇总展示并可下载/删除。
 * 实现方必须兜底异常：登记失败只记日志，绝不阻断业务上传链路。
 */
public interface FileRegistry {

    /**
     * 登记一个已上传成功的文件。
     *
     * @param bucket     存储桶
     * @param storageKey 对象 key
     * @param fileName   原始文件名
     * @param contentType MIME 类型（可空）
     * @param size       字节数
     * @param bizType    业务来源（ota / generic / ...）
     * @param bizRefId   业务引用 ID（如固件包 id，可空）
     */
    void register(String bucket, String storageKey, String fileName,
                  String contentType, long size, String bizType, Long bizRefId);

    /** 业务删除时按（业务类型 + 引用 ID）移除登记（实现方不删对象，只删登记记录）。 */
    void removeByBiz(String bizType, Long bizRefId);
}
