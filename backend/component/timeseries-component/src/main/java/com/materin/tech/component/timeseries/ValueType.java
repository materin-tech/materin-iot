package com.materin.tech.component.timeseries;

/** 时序列值类型（TSDB 无关，具体实现自行映射存储类型）。 */
public enum ValueType {

    LONG, DOUBLE, BOOLEAN, TEXT;

    /**
     * 物模型 dataType → 值类型（宽容解析）：
     * int/long → LONG；float/double/number → DOUBLE；boolean → BOOLEAN；其余 → TEXT。
     * dataType 可能是类型字符串，也可能是 {type: ...} 结构的 Map 序列化前缀，由调用方先行归一。
     */
    public static ValueType of(String raw) {
        if (raw == null || raw.isBlank()) {
            return TEXT;
        }
        return switch (raw.trim().toLowerCase()) {
            case "int", "integer", "long" -> LONG;
            case "float", "double", "number" -> DOUBLE;
            case "bool", "boolean" -> BOOLEAN;
            default -> TEXT;
        };
    }
}
