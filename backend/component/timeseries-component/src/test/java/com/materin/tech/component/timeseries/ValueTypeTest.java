package com.materin.tech.component.timeseries;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ValueType 物模型类型映射测试。 */
class ValueTypeTest {

    @Test
    void of_shouldMapCommonDataTypes() {
        assertThat(ValueType.of("int")).isEqualTo(ValueType.LONG);
        assertThat(ValueType.of("long")).isEqualTo(ValueType.LONG);
        assertThat(ValueType.of("double")).isEqualTo(ValueType.DOUBLE);
        assertThat(ValueType.of("float")).isEqualTo(ValueType.DOUBLE);
        assertThat(ValueType.of("boolean")).isEqualTo(ValueType.BOOLEAN);
        assertThat(ValueType.of("bool")).isEqualTo(ValueType.BOOLEAN);
        assertThat(ValueType.of("string")).isEqualTo(ValueType.TEXT);
        assertThat(ValueType.of("enum")).isEqualTo(ValueType.TEXT);
        assertThat(ValueType.of("struct")).isEqualTo(ValueType.TEXT);
        assertThat(ValueType.of("INT")).isEqualTo(ValueType.LONG);
        assertThat(ValueType.of(null)).isEqualTo(ValueType.TEXT);
        assertThat(ValueType.of("  ")).isEqualTo(ValueType.TEXT);
    }
}
