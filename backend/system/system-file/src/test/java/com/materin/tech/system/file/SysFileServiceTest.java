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
package com.materin.tech.system.file;

import com.materin.tech.common.exception.BizException;
import com.materin.tech.component.storage.StorageClient;
import com.materin.tech.component.storage.StorageProperties;
import com.materin.tech.system.file.entity.SysFile;
import com.materin.tech.system.file.mapper.SysFileMapper;
import com.materin.tech.system.file.service.SysFileService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysFileServiceTest {

    @Mock
    private SysFileMapper mapper;

    @Mock
    private ObjectProvider<StorageClient> storage;

    @Mock
    private StorageClient storageClient;

    @Mock
    private StorageProperties storageProperties;

    private SysFileService service;

    @BeforeEach
    void setUp() {
        service = new SysFileService(mapper, storage, storageProperties);
    }

    private SysFile sample() {
        SysFile f = new SysFile();
        f.setId(1L);
        f.setFileName("fw.bin");
        f.setBucket("materin");
        f.setStorageKey("ota/1/fw.bin");
        f.setSize(10L);
        return f;
    }

    @Test
    @DisplayName("list 返回 {items,total} 分页信封")
    void list_returnsPageResultEnvelope() {
        Page<SysFile> p = Page.of(1, 10, 1);
        p.setRecords(List.of(sample()));
        when(mapper.paginate(eq(1L), eq(10L), any(QueryWrapper.class))).thenReturn(p);

        var result = service.list(1, 10, "ota", null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("downloadUrl 走预签名")
    void downloadUrl_returnsPresignedUrl() {
        when(mapper.selectOneById(1L)).thenReturn(sample());
        when(storage.getIfAvailable()).thenReturn(storageClient);
        when(storageClient.presignedGetUrl("materin", "ota/1/fw.bin", 600))
                .thenReturn("https://signed/u");

        assertThat(service.downloadUrl(1L, 600)).isEqualTo("https://signed/u");
    }

    @Test
    @DisplayName("存储中间件不可用时 downloadUrl 抛 503")
    void downloadUrl_throwsWhenStorageUnavailable() {
        when(mapper.selectOneById(1L)).thenReturn(sample());
        when(storage.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.downloadUrl(1L, 600))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("存储中间件不可用");
    }

    @Test
    @DisplayName("文件不存在抛 404")
    void downloadUrl_throwsWhenFileMissing() {
        when(mapper.selectOneById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.downloadUrl(99L, 600))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    @DisplayName("delete 先删对象存储再删记录")
    void delete_removesObjectThenRecord() {
        when(mapper.selectOneById(1L)).thenReturn(sample());
        when(storage.getIfAvailable()).thenReturn(storageClient);

        service.delete(1L);

        verify(storageClient).deleteObject("materin", "ota/1/fw.bin");
        verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("对象存储删除失败仍删登记记录（降级不阻塞）")
    void delete_storageFailureStillRemovesRecord() {
        when(mapper.selectOneById(1L)).thenReturn(sample());
        when(storage.getIfAvailable()).thenReturn(storageClient);
        doThrow(new RuntimeException("boom")).when(storageClient)
                .deleteObject("materin", "ota/1/fw.bin");

        service.delete(1L);

        verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("存储中间件不可用时 delete 只删登记记录")
    void delete_withoutStorageOnlyRemovesRecord() {
        when(mapper.selectOneById(1L)).thenReturn(sample());
        when(storage.getIfAvailable()).thenReturn(null);

        service.delete(1L);

        verify(storageClient, never()).deleteObject("materin", "ota/1/fw.bin");
        verify(mapper).deleteById(1L);
    }
}
