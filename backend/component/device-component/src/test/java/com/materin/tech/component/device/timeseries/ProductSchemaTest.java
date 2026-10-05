package com.materin.tech.component.device.timeseries;

import com.materin.tech.common.spi.ThingModelLookup;
import com.materin.tech.component.timeseries.TimeSeriesColumn;
import com.materin.tech.component.timeseries.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 物模型 → 时序列定义解析测试（宽容解析契约）。 */
class ProductSchemaTest {

    @Test
    void parse_shouldMapPropertyColumns() {
        var digest = new ThingModelLookup.ThingModelDigest(1L,
                List.of(Map.of("identifier", "temperature", "name", "温度",
                                "dataType", "double"),
                        Map.of("identifier", "switch", "name", "开关",
                                "dataType", Map.of("type", "boolean"))),
                List.of(), List.of());
        ProductSchema schema = ProductSchema.parse(1L, digest);

        assertThat(schema.property().columns()).extracting(TimeSeriesColumn::name)
                .containsExactly("temperature", "switch");
        assertThat(schema.property().columns()).extracting(TimeSeriesColumn::type)
                .containsExactly(ValueType.DOUBLE, ValueType.BOOLEAN);
    }
    @Test
    void parse_shouldFlattenEventOutputsAndFallback() {
        var digest = new ThingModelLookup.ThingModelDigest(1L, List.of(),
                List.of(),
                List.of(Map.of("identifier", "overheat", "name", "过热",
                                "outputData", List.of(Map.of("identifier", "temp", "dataType", "double"))),
                        Map.of("identifier", "boot", "name", "启动")));
        ProductSchema schema = ProductSchema.parse(1L, digest);

        assertThat(schema.event().columns()).extracting(TimeSeriesColumn::name)
                .containsExactly("evt_overheat_temp", "evt_boot_data");
        assertThat(schema.event().columns()).extracting(TimeSeriesColumn::type)
                .containsExactly(ValueType.DOUBLE, ValueType.TEXT);
    }

    @Test
    void parse_shouldFlattenMethodInOutAndFixedColumns() {
        var digest = new ThingModelLookup.ThingModelDigest(1L, List.of(),
                List.of(Map.of("identifier", "reboot",
                                "inputData", List.of(Map.of("identifier", "delay", "dataType", "int")),
                                "outputData", List.of(Map.of("identifier", "ack", "dataType", "boolean")))),
                List.of());
        ProductSchema schema = ProductSchema.parse(1L, digest);
        assertThat(schema.method().columns()).extracting(TimeSeriesColumn::name)
                .containsExactly("cmd_reboot_in_delay", "cmd_reboot_out_ack",
                        "cmd_reboot_req", "cmd_reboot_resp", "cmd_reboot_success", "cmd_reboot_cost_ms");
        assertThat(schema.method().columns()).extracting(TimeSeriesColumn::type)
                .containsExactly(ValueType.LONG, ValueType.BOOLEAN,
                        ValueType.TEXT, ValueType.TEXT, ValueType.BOOLEAN, ValueType.LONG);
    }

    @Test
    void parse_shouldTolerateMissingModel() {
        ProductSchema schema = ProductSchema.empty(9L);
        assertThat(schema.property().columns()).isEmpty();
        assertThat(schema.event().columns()).isEmpty();
        assertThat(schema.method().columns()).isEmpty();
    }
}
