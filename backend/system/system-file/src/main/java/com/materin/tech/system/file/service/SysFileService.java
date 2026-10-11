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
package com.materin.tech.system.file.service;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.component.storage.StorageClient;
import com.materin.tech.component.storage.StorageProperties;
import com.materin.tech.system.file.entity.SysFile;
import com.materin.tech.system.file.mapper.SysFileMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 系统文件管理服务：全平台上传文件的查询 / 下载 / 删除。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileService {

    private final SysFileMapper mapper;
    private final ObjectProvider<StorageClient> storage;
    private final StorageProperties storageProperties;

    /** 分页列表：bizType 精确过滤，keyword 模糊匹配文件名或 storage key。 */
    public PageResult<SysFile> list(long page, long pageSize, String bizType, String keyword) {
        QueryWrapper qw = QueryWrapper.create();
        if (bizType != null && !bizType.isBlank()) {
            qw.eq("biz_type", bizType);
        }
        if (keyword != null && !keyword.isBlank()) {
            com.mybatisflex.core.query.QueryColumn nameCol = new com.mybatisflex.core.query.QueryColumn("file_name");
            com.mybatisflex.core.query.QueryColumn keyCol = new com.mybatisflex.core.query.QueryColumn("storage_key");
            qw.and(nameCol.like(keyword).or(keyCol.like(keyword)));
        }
        Page<SysFile> p = mapper.paginate(page, pageSize, qw.orderBy("id", false));
        return new PageResult<>(p.getRecords(), p.getTotalRow());
    }

    public SysFile requireById(Long id) {
        SysFile f = mapper.selectOneById(id);
        if (f == null) {
            throw new BizException(404, "文件不存在: " + id);
        }
        return f;
    }

    /** 下载地址（预签名，短期有效）。 */
    public String downloadUrl(Long id, int expireSeconds) {
        SysFile f = requireById(id);
        StorageClient client = storage.getIfAvailable();
        if (client == null) {
            throw new BizException(503, "存储中间件不可用");
        }
        return client.presignedGetUrl(f.getBucket(), f.getStorageKey(), expireSeconds);
    }

    /** 删除：先删对象存储文件，再删登记记录（对象删除失败仍删记录，与 OTA 固件包删除语义一致）。 */
    public void delete(Long id) {
        SysFile f = requireById(id);
        StorageClient client = storage.getIfAvailable();
        if (client != null) {
            try {
                client.deleteObject(f.getBucket(), f.getStorageKey());
            } catch (Exception e) {
                log.warn("对象存储文件删除失败（记录仍删除）: key={}, {}", f.getStorageKey(), e.getMessage());
            }
        }
        mapper.deleteById(id);
    }
}
