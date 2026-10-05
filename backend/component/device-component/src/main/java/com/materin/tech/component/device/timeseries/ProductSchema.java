package com.materin.tech.component.device.timeseries;

import com.materin.tech.common.spi.ThingModelLookup;
import com.materin.tech.component.timeseries.TimeSeriesColumn;
import com.materin.tech.component.timeseries.TimeSeriesMetadata;
import com.materin.tech.component.timeseries.ValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 产品级时序模式：由已发布物模型解析出三类时序元数据（同一设备维度、同一套列逻辑）：
 * - device_property：属性 → 测量列 {identifier}
 * - device_event   ：事件 → evt_{事件id}_{参数}（无出参定义时兜底 evt_{事件id}_data TEXT）
 * - device_method  ：方法 → cmd_{方法id}_{in|out}_{参数} + req/resp/success/cost_ms
 * 解析宽容：identifier 取 identifier/id/name 之一，dataType 兼容字符串与 {type:...} 结构。
 */
public record ProductSchema(TimeSeriesMetadata property,
                            TimeSeriesMetadata event,
                            TimeSeriesMetadata method) {

    public static ProductSchema parse(Long productId, ThingModelLookup.ThingModelDigest model) {
        return new ProductSchema(
                new TimeSeriesMetadata("device_property", String.valueOf(productId),
                        parseProperties(model.properties())),
                new TimeSeriesMetadata("device_event", String.valueOf(productId),
                        parseEvents(model.events())),
                new TimeSeriesMetadata("device_method", String.valueOf(productId),
                        parseMethods(model.methods())));
    }

    /** 无已发布物模型时的空模式（查询仍可用，写入全部走 TEXT 兜底）。 */
    public static ProductSchema empty(Long productId) {
        return parse(productId, new ThingModelLookup.ThingModelDigest(
                productId, List.of(), List.of(), List.of()));
    }

    // ------------------------------------------------------------ 属性

    static List<TimeSeriesColumn> parseProperties(List<Map<String, Object>> properties) {
        List<TimeSeriesColumn> columns = new ArrayList<>();
        if (properties == null) {
            return columns;
        }
        for (Map<String, Object> item : properties) {
            String identifier = identifierOf(item);
            if (identifier == null) {
                continue;
            }
            columns.add(new TimeSeriesColumn(identifier, dataTypeOf(item.get("dataType")), nameOf(item)));
        }
        return columns;
    }

    // ------------------------------------------------------------ 事件

    static List<TimeSeriesColumn> parseEvents(List<Map<String, Object>> events) {
        List<TimeSeriesColumn> columns = new ArrayList<>();
        if (events == null) {
            return columns;
        }
        for (Map<String, Object> item : events) {
            String identifier = identifierOf(item);
            if (identifier == null) {
                continue;
            }
            List<Map<String, Object>> outputs = firstListOf(item, "outputData", "output", "outputs", "params");
            if (outputs.isEmpty()) {
                // 无出参定义：整包数据以 TEXT 落一列，保证事件必达时序库
                columns.add(new TimeSeriesColumn("evt_" + identifier + "_data", ValueType.TEXT, nameOf(item)));
                continue;
            }
            for (Map<String, Object> param : outputs) {
                String paramId = identifierOf(param);
                if (paramId == null) {
                    continue;
                }
                columns.add(new TimeSeriesColumn("evt_" + identifier + "_" + paramId,
                        dataTypeOf(param.get("dataType")), nameOf(param)));
            }
        }
        return columns;
    }

    // ------------------------------------------------------------ 方法

    static List<TimeSeriesColumn> parseMethods(List<Map<String, Object>> methods) {
        List<TimeSeriesColumn> columns = new ArrayList<>();
        if (methods == null) {
            return columns;
        }
        for (Map<String, Object> item : methods) {
            String identifier = identifierOf(item);
            if (identifier == null) {
                continue;
            }
            for (Map<String, Object> param : firstListOf(item, "inputData", "input", "inputs", "params")) {
                String paramId = identifierOf(param);
                if (paramId != null) {
                    columns.add(new TimeSeriesColumn("cmd_" + identifier + "_in_" + paramId,
                            dataTypeOf(param.get("dataType")), nameOf(param)));
                }
            }
            for (Map<String, Object> param : firstListOf(item, "outputData", "output", "outputs")) {
                String paramId = identifierOf(param);
                if (paramId != null) {
                    columns.add(new TimeSeriesColumn("cmd_" + identifier + "_out_" + paramId,
                            dataTypeOf(param.get("dataType")), nameOf(param)));
                }
            }
            // 调用记录固定列
            columns.add(new TimeSeriesColumn("cmd_" + identifier + "_req", ValueType.TEXT, "请求"));
            columns.add(new TimeSeriesColumn("cmd_" + identifier + "_resp", ValueType.TEXT, "应答"));
            columns.add(new TimeSeriesColumn("cmd_" + identifier + "_success", ValueType.BOOLEAN, "成功"));
            columns.add(new TimeSeriesColumn("cmd_" + identifier + "_cost_ms", ValueType.LONG, "耗时ms"));
        }
        return columns;
    }

    // ------------------------------------------------------------ 宽容解析工具

    /** 物模型项标识：identifier / id / name 依次取第一个非空。 */
    static String identifierOf(Map<String, Object> item) {
        for (String key : List.of("identifier", "id", "name")) {
            Object value = item.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static String nameOf(Map<String, Object> item) {
        Object name = item.get("name");
        return name == null ? null : String.valueOf(name);
    }

    /** dataType 兼容两种形态："double" 或 {type: "double"}。 */
    static ValueType dataTypeOf(Object dataType) {
        if (dataType instanceof Map<?, ?> map && map.get("type") != null) {
            return ValueType.of(String.valueOf(map.get("type")));
        }
        return ValueType.of(dataType == null ? null : String.valueOf(dataType));
    }

    /** 取第一个存在且为 List 的字段。 */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> firstListOf(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        }
        return List.of();
    }
}
