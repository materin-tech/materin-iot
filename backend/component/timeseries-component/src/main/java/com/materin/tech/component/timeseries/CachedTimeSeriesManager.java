package com.materin.tech.component.timeseries;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 时序管理器缓存基类：同一模型（metric:modelId）复用同一服务实例。 */
public abstract class CachedTimeSeriesManager implements TimeSeriesManager {

    private final ConcurrentMap<String, TimeSeriesService> services = new ConcurrentHashMap<>();

    @Override
    public TimeSeriesService getTimeSeries(TimeSeriesMetadata metadata) {
        return services.computeIfAbsent(metadata.cacheKey(), key -> createService(metadata));
    }

    /** 创建指定模型的时序服务（由具体 TSDB 实现提供）。 */
    protected abstract TimeSeriesService createService(TimeSeriesMetadata metadata);
}
