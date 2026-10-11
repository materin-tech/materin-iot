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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 存储唯一性守卫单测：多实现共存必须启动失败。 */
class StorageUniquenessGuardTest {

    private StorageClient fakeClient(String name) {
        return new StorageClient() {
            @Override
            public String putObject(String b, String k, InputStream i, long s, String c) { return k; }
            @Override
            public InputStream getObject(String b, String k) { return null; }
            @Override
            public void deleteObject(String b, String k) { }
            @Override
            public StorageStat statObject(String b, String k) { return null; }
            @Override
            public String presignedGetUrl(String b, String k, int e) { return ""; }
            @Override
            public String toString() { return name; }
        };
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StorageClient> providerOf(StorageClient... clients) {
        ObjectProvider<StorageClient> p = mock(ObjectProvider.class);
        when(p.stream()).thenReturn(Stream.of(clients));
        return p;
    }

    private InitializingBean guard(StorageProperties.Type type, StorageClient... clients) {
        StorageProperties props = new StorageProperties();
        props.setType(type);
        StorageAutoConfiguration config = new StorageAutoConfiguration();
        return config.storageUniquenessGuard(providerOf(clients), props);
    }

    @Test
    @DisplayName("两个存储实现同时激活 → 守卫抛异常阻止启动")
    void guard_shouldFailWhenMultipleClients() {
        assertThatThrownBy(() -> guard(StorageProperties.Type.MINIO,
                fakeClient("minio"), fakeClient("oss")).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只允许启用一个存储器");
    }

    @Test
    @DisplayName("type 指定实现但 classpath 无实现 → 守卫抛异常")
    void guard_shouldFailWhenNoneButTypeSet() {
        assertThatThrownBy(() -> guard(StorageProperties.Type.MINIO).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有任何 StorageClient 实现");
    }

    @Test
    @DisplayName("恰好一个实现 + type=NONE → 守卫放行")
    void guard_shouldPassWithSingleClientAndNone() throws Exception {
        guard(StorageProperties.Type.NONE, fakeClient("minio")).afterPropertiesSet();
    }
}
