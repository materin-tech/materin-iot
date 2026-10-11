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
