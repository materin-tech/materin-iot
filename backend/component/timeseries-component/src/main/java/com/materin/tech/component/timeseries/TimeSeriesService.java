package com.materin.tech.component.timeseries;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 单个时序模型的存取服务（由 TimeSeriesManager 按 TimeSeriesMetadata 创建并缓存）。
 * 实现方负责列（时序）的存在性保障与类型转换。
 */
public interface TimeSeriesService {

    /** 写入单条（实现方保证列不存在时按元数据类型自动创建）。 */
    void save(TimeSeriesData data);

    /** 批量写入（默认逐条，实现方可覆写优化）。 */
    default void save(Collection<TimeSeriesData> batch) {
        if (batch == null) {
            return;
        }
        batch.forEach(this::save);
    }

    /**
     * 查询历史数据。
     *
     * @return 每行一个 Map（必含 time 字段；列名为短名），空集表示无数据
     */
    List<Map<String, Object>> query(TimeSeriesQuery query);
}
