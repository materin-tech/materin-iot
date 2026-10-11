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

import java.util.Optional;

/** 跨组件协作 SPI：设备侧反查产品信息（由 product-component 实现）。 */
public interface ProductLookup {

    Optional<ProductSummary> findById(String productId);

    /** 按产品 Key 查产品（网关代子设备注册时声明产品用），默认空实现。 */
    default Optional<ProductSummary> findByKey(String productKey) {
        return Optional.empty();
    }

    record ProductSummary(String id, String name, String productKey, String nodeType) {

        /** 兼容旧构造（nodeType 缺省 DIRECT） */
        public ProductSummary(String id, String name, String productKey) {
            this(id, name, productKey, "DIRECT");
        }

    }
}
