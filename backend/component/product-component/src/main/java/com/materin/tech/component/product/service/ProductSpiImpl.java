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
                    product.getName(), product.getProductKey()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
