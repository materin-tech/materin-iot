package com.materin.tech.component.timeseries;

/**
 * 时序管理器统一入口（对标 jetlinks-community TimeSeriesManager）：
 * 按 TimeSeriesMetadata 获取（并缓存）对应模型的存取服务。
 */
public interface TimeSeriesManager {

    /** 获取指定模型的时序服务（同一模型复用同一实例）。 */
    TimeSeriesService getTimeSeries(TimeSeriesMetadata metadata);

    /** 预注册模型（建库/校验列定义），实现方可为空操作（写入时懒建）。 */
    default void register(TimeSeriesMetadata metadata) {
    }

    /** 删除业务主键下的全部时序数据（如设备删除后清理）。 */
    default void remove(String metric, String modelId, String id) {
    }
}
