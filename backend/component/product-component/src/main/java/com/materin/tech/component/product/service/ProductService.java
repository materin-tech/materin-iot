package com.materin.tech.component.product.service;

import com.materin.tech.common.core.PageResult;
import com.materin.tech.common.exception.BizException;
import com.materin.tech.common.spi.ProductDeviceCounter;
import com.materin.tech.component.product.dto.ProductDtos.ProductItem;
import com.materin.tech.component.product.dto.ProductDtos.ProductUpsertRequest;
import com.materin.tech.component.product.entity.Product;
import com.materin.tech.component.product.mapper.ProductMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 产品管理。 */
@Service
public class ProductService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductMapper productMapper;
    private final ProductDeviceCounter deviceCounter;

    public ProductService(ProductMapper productMapper,
                          ObjectProvider<ProductDeviceCounter> counter) {
        this.productMapper = productMapper;
        this.deviceCounter = counter.getIfAvailable();
    }

    public PageResult<ProductItem> list(long page, long pageSize, String name, String productKey,
                                        String source, Integer status, String startTime, String endTime) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (StringUtils.hasText(name)) {
            String like = "%" + name + "%";
            wrapper.and(new QueryColumn("name").like(like)
                    .or(new QueryColumn("product_key").like(like)));
        }
        if (StringUtils.hasText(productKey)) {
            wrapper.eq("product_key", productKey);
        }
        if (StringUtils.hasText(source)) {
            wrapper.eq("source", source);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge("create_time", LocalDateTime.parse(startTime, TS));
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le("create_time", LocalDateTime.parse(endTime, TS));
        }
        Page<Product> result = productMapper.paginate(Page.of(page, pageSize), wrapper);
        List<ProductItem> items = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(items, result.getTotalRow());
    }

    public ProductItem create(ProductUpsertRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw BizException.badRequest("产品名不能为空");
        }
        String key = StringUtils.hasText(request.productKey()) ? request.productKey() : generateKey();
        if (productMapper.selectCountByQuery(QueryWrapper.create().eq("product_key", key)) > 0) {
            throw BizException.badRequest("产品标识已存在: " + key);
        }
        Product product = new Product();
        apply(product, request);
        product.setProductKey(key);
        product.setDeviceCount(0);
        productMapper.insert(product);
        return toItem(product);
    }

    public ProductItem update(Long id, ProductUpsertRequest request) {
        Product product = requireProduct(id);
        if (StringUtils.hasText(request.productKey())
                && !request.productKey().equals(product.getProductKey())
                && productMapper.selectCountByQuery(
                        QueryWrapper.create().eq("product_key", request.productKey())) > 0) {
            throw BizException.badRequest("产品标识已存在: " + request.productKey());
        }
        apply(product, request);
        productMapper.update(product);
        return toItem(product);
    }

    public void delete(Long id) {
        requireProduct(id);
        productMapper.deleteById(id);
    }

    public Map<String, Integer> importProducts(String source, List<Map<String, Object>> items) {
        int failed = 0;
        int imported = 0;
        for (Map<String, Object> item : items) {
            try {
                String name = (String) item.get("name");
                if (!StringUtils.hasText(name)) {
                    failed++;
                    continue;
                }
                Product product = new Product();
                product.setName(name);
                Object key = item.get("productKey");
                product.setProductKey(key == null || String.valueOf(key).isBlank()
                        ? generateKey() : String.valueOf(key));
                product.setCategory((String) item.get("category"));
                product.setProtocol((String) item.get("protocol"));
                product.setStatus(item.get("status") == null ? 0
                        : ((Number) item.get("status")).intValue());
                product.setSource(source);
                product.setDeviceCount(0);
                productMapper.insert(product);
                imported++;
            } catch (Exception e) {
                failed++;
            }
        }
        return Map.of("imported", imported, "failed", failed);
    }

    private void apply(Product product, ProductUpsertRequest request) {
        if (StringUtils.hasText(request.name())) {
            product.setName(request.name());
        }
        if (request.category() != null) {
            product.setCategory(request.category());
        }
        if (request.protocol() != null) {
            product.setProtocol(request.protocol());
        }
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        if (request.source() != null) {
            product.setSource(request.source());
        }
        if (request.remark() != null) {
            product.setRemark(request.remark());
        }
    }

    private Product requireProduct(Long id) {
        Product product = productMapper.selectOneByQuery(QueryWrapper.create().eq("id", id));
        if (product == null) {
            throw BizException.notFound("产品不存在: " + id);
        }
        return product;
    }

    private String generateKey() {
        return "PK" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase();
    }

    private ProductItem toItem(Product product) {
        String createTime = product.getCreateTime() == null ? null
                : product.getCreateTime().format(TS);
        return new ProductItem(product.getId(), product.getName(), product.getProductKey(),
                product.getCategory(), product.getProtocol(), product.getStatus(),
                product.getDeviceCount(), product.getSource(), product.getRemark(), createTime);
    }
}
