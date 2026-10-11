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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 存储装配（enabled=false 时整段关闭，上层 ObjectProvider 判空降级）。
 * 实现子模块各自提供 StorageClient Bean（按 materin.storage.type 条件互斥激活），
 * 使用方一律 ObjectProvider<StorageClient> 判空降级。
 */
@Configuration
@ConditionalOnProperty(prefix = "materin.storage", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    /**
     * 唯一性守卫：后端只允许启用一个存储实现。
     * 多个 StorageClient 同时激活（配置错误/依赖冲突）→ 启动失败并明确报错；
     * type 指定了实现但 classpath 上没有 → 同样启动失败（尽早暴露配置错误）。
     */
    @Bean
    public InitializingBean storageUniquenessGuard(ObjectProvider<StorageClient> candidates,
                                                   StorageProperties properties) {
        return () -> {
            List<StorageClient> all = candidates.stream().toList();
            if (all.size() > 1) {
                throw new IllegalStateException(
                        "检测到 " + all.size() + " 个存储实现（"
                                + all.stream().map(c -> c.getClass().getName()).toList()
                                + "）。后端只允许启用一个存储器：请检查 materin.storage.type 配置与 "
                                + "storage-* 依赖，各实现按 type 互斥激活，禁止同时存在多个 StorageClient。");
            }
            if (all.isEmpty() && properties.getType() != StorageProperties.Type.NONE) {
                throw new IllegalStateException(
                        "materin.storage.type=" + properties.getType()
                                + " 但 classpath 上没有任何 StorageClient 实现（缺少对应 storage-* 依赖）");
            }
        };
    }
}
