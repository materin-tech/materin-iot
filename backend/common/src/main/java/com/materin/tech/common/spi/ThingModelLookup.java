package com.materin.tech.common.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 跨组件 SPI：按产品查询已发布物模型。
 * 产品组件实现，设备→时序桥接据此解析时序列定义。
 */
public interface ThingModelLookup {

    /** 查询产品已发布（status=1）的物模型，多版本取最新；无已发布模型返回 empty。 */
    Optional<ThingModelDigest> findPublishedByProductId(Long productId);

    /** 物模型摘要：属性/方法/事件保持原始 JSON 结构（List&lt;Map&gt;），由消费方自行解析。 */
    record ThingModelDigest(Long productId,
                            List<Map<String, Object>> properties,
                            List<Map<String, Object>> methods,
                            List<Map<String, Object>> events) {
    }
}
