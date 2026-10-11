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
package com.materin.tech.system.file.registry;

import com.materin.tech.common.spi.FileRegistry;
import com.materin.tech.system.file.entity.SysFile;
import com.materin.tech.system.file.mapper.SysFileMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件登记 SPI 实现：业务方（OTA 等）上传成功后登记，删除时按业务引用移除。
 * 登记失败只记日志，不阻断业务上传链路（SPI 契约）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileRegistryImpl implements FileRegistry {

    private final SysFileMapper mapper;

    @Override
    public void register(String bucket, String storageKey, String fileName,
                         String contentType, long size, String bizType, Long bizRefId) {
        try {
            SysFile f = new SysFile();
            f.setBucket(bucket);
            f.setStorageKey(storageKey);
            f.setFileName(fileName == null || fileName.isBlank()
                    ? storageKey.substring(storageKey.lastIndexOf('/') + 1) : fileName);
            f.setContentType(contentType);
            f.setSize(size);
            f.setBizType(bizType == null || bizType.isBlank() ? "generic" : bizType);
            f.setBizRefId(bizRefId);
            mapper.insert(f);
        } catch (Exception e) {
            log.warn("文件登记失败（不影响业务）: key={}, {}", storageKey, e.getMessage());
        }
    }

    @Override
    public void removeByBiz(String bizType, Long bizRefId) {
        try {
            mapper.deleteByQuery(QueryWrapper.create()
                    .eq("biz_type", bizType).eq("biz_ref_id", bizRefId));
        } catch (Exception e) {
            log.warn("文件登记移除失败（不影响业务）: biz={}/{}, {}", bizType, bizRefId, e.getMessage());
        }
    }
}
