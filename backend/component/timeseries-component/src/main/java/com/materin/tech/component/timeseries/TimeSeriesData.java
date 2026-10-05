package com.materin.tech.component.timeseries;

import java.util.Map;

/**
 * 单条时序数据。
 *
 * @param id   业务主键（设备维度场景 = 设备 ID）
 * @param time 时间戳（epoch 毫秒）
 * @param data 键值数据（key = 列标识，value 基础类型或字符串）
 */
public record TimeSeriesData(String id, long time, Map<String, Object> data) {

    public TimeSeriesData {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("时序数据业务主键不能为空");
        }
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
