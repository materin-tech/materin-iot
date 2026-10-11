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

import com.materin.tech.system.file.entity.SysFile;
import com.materin.tech.system.file.mapper.SysFileMapper;
import com.materin.tech.system.file.registry.FileRegistryImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FileRegistryImplTest {

    @Mock
    private SysFileMapper mapper;

    private FileRegistryImpl registry;

    @BeforeEach
    void setUp() {
        registry = new FileRegistryImpl(mapper);
    }

    @Test
    @DisplayName("register 落库完整元数据，空文件名时从 storageKey 推导")
    void register_persistsMetadata() {
        registry.register("bucket-a", "ota/1/2026/x-abc-firmware.bin",
                null, "application/octet-stream", 1024L, "ota", 9L);

        ArgumentCaptor<SysFile> captor = ArgumentCaptor.forClass(SysFile.class);
        verify(mapper).insert(captor.capture());
        SysFile saved = captor.getValue();
        assertThat(saved.getBucket()).isEqualTo("bucket-a");
        assertThat(saved.getStorageKey()).isEqualTo("ota/1/2026/x-abc-firmware.bin");
        assertThat(saved.getFileName()).isEqualTo("x-abc-firmware.bin");
        assertThat(saved.getSize()).isEqualTo(1024L);
        assertThat(saved.getBizType()).isEqualTo("ota");
        assertThat(saved.getBizRefId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("bizType 为空时兜底为 generic")
    void register_blankBizTypeFallsBackToGeneric() {
        registry.register("bucket-a", "misc/k.bin", "k.bin", null, 1L, " ", null);

        ArgumentCaptor<SysFile> captor = ArgumentCaptor.forClass(SysFile.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getBizType()).isEqualTo("generic");
    }

    @Test
    @DisplayName("登记失败不抛出（不阻断业务上传链路）")
    void register_failureDoesNotPropagate() {
        doThrow(new RuntimeException("db down")).when(mapper).insert(any(SysFile.class));

        assertThatCode(() -> registry.register("b", "k", "f", null, 1L, "ota", 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("removeByBiz 按业务类型 + 引用 ID 删除")
    void removeByBiz_deletesByBizRef() {
        registry.removeByBiz("ota", 7L);

        verify(mapper).deleteByQuery(any(QueryWrapper.class));
    }
}
