package com.materin.tech.component.timeseries;

/** 时序列定义：name 为列（测量）标识，type 为值类型，alias 为展示名（可空）。 */
public record TimeSeriesColumn(String name, ValueType type, String alias) {

    public TimeSeriesColumn {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("时序列名不能为空");
        }
        type = type == null ? ValueType.TEXT : type;
    }

    public static TimeSeriesColumn of(String name, ValueType type) {
        return new TimeSeriesColumn(name, type, null);
    }
}
