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
package com.materin.tech.component.product.service;

import com.materin.tech.common.spi.ProductDeviceCounter;
import com.materin.tech.common.spi.ProductLookup;
import com.materin.tech.component.product.entity.Product;
import com.materin.tech.component.product.mapper.ProductMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 跨组件 SPI 实现：产品侧供设备组件调用。 */
@Component
@RequiredArgsConstructor
public class ProductSpiImpl implements ProductDeviceCounter, ProductLookup {

    private final ProductMapper productMapper;

    @Override
    public void onDeviceAdded(String productId) {
        try {
            // DB 原子自增：多实例并发安全，无需分布式锁
            productMapper.incrementDeviceCount(Long.parseLong(productId));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void onDeviceRemoved(String productId) {
        try {
            productMapper.decrementDeviceCount(Long.parseLong(productId));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public Optional<ProductSummary> findById(String productId) {
        try {
            long id = Long.parseLong(productId);
            Product product = productMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
            if (product == null) {
                return Optional.empty();
            }
            return Optional.of(new ProductSummary(String.valueOf(product.getId()),
                    product.getName(), product.getProductKey(), product.getNodeType()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ProductSummary> findByKey(String productKey) {
        Product product = productMapper.selectOneByQuery(
                QueryWrapper.create().eq("product_key", productKey));
        if (product == null) {
            return Optional.empty();
        }
        return Optional.of(new ProductSummary(String.valueOf(product.getId()),
                product.getName(), product.getProductKey(), product.getNodeType()));
    }
}
