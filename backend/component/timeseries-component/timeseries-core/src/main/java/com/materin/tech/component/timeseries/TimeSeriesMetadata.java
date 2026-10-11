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
package com.materin.tech.component.timeseries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 时序元数据（对标 jetlinks-community TimeSeriesMetadata）：
 * metric 为模型类型（如 device_property / device_event / device_method），
 * modelId 为模型实例（设备场景 = 产品 ID），columns 为该模型的列定义。
 *
 * @param metric  模型类型标识
 * @param modelId 模型实例标识
 * @param columns 列定义（驱动实现方建表/类型映射）
 */
public record TimeSeriesMetadata(String metric, String modelId, List<TimeSeriesColumn> columns) {

    public TimeSeriesMetadata {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("metric 不能为空");
        }
        modelId = modelId == null ? "_" : modelId;
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    /** 列名 → 列定义（重名取首个）。 */
    public Map<String, TimeSeriesColumn> columnMap() {
        Map<String, TimeSeriesColumn> map = new LinkedHashMap<>();
        for (TimeSeriesColumn column : columns) {
            map.putIfAbsent(column.name(), column);
        }
        return map;
    }

    /** 实现方服务缓存键。 */
    public String cacheKey() {
        return metric + ":" + modelId;
    }
}
