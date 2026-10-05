package com.materin.tech.component.timeseries;

import java.util.List;

/**
 * 时序查询条件。
 *
 * @param id             业务主键（设备维度场景 = 设备 ID，必填）
 * @param start          起始时间（epoch 毫秒，含），可空
 * @param end            结束时间（epoch 毫秒，含），可空
 * @param keys           指定列（测量）标识，空 = 全部列
 * @param keyPrefix      列前缀过滤（如 evt_ / cmd_），keys 非空时忽略
 * @param limit          返回条数上限，可空（实现方默认）
 * @param desc           是否按时间倒序
 * @param aggFunction    聚合函数（avg/sum/max/min/count...），空 = 原始数据
 * @param aggIntervalMs  聚合窗口（毫秒），aggFunction 非空时必填
 */
public record TimeSeriesQuery(String id, Long start, Long end, List<String> keys,
                              String keyPrefix, Integer limit, boolean desc,
                              String aggFunction, Long aggIntervalMs) {

    public TimeSeriesQuery {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("查询业务主键不能为空");
        }
        keys = keys == null ? List.of() : List.copyOf(keys);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 流式构建（字段较多，避免超长构造参数）。 */
    public static final class Builder {

        private final String id;
        private Long start;
        private Long end;
        private List<String> keys = List.of();
        private String keyPrefix;
        private Integer limit;
        private boolean desc;
        private String aggFunction;
        private Long aggIntervalMs;

        private Builder(String id) {
            this.id = id;
        }

        public Builder start(Long start) {
            this.start = start;
            return this;
        }

        public Builder end(Long end) {
            this.end = end;
            return this;
        }

        public Builder keys(List<String> keys) {
            this.keys = keys == null ? List.of() : keys;
            return this;
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder desc(boolean desc) {
            this.desc = desc;
            return this;
        }

        public Builder aggregation(String function, Long intervalMs) {
            this.aggFunction = function;
            this.aggIntervalMs = intervalMs;
            return this;
        }

        public TimeSeriesQuery build() {
            return new TimeSeriesQuery(id, start, end, keys, keyPrefix, limit,
                    desc, aggFunction, aggIntervalMs);
        }
    }
}
