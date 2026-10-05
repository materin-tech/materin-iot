package com.materin.tech.component.iotdb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** IoTDB 路径安全化测试。 */
class IotdbPathBuilderTest {

    @Test
    void safe_shouldSanitizeIllegalChars() {
        assertThat(IotdbPathBuilder.safe("dev-001")).isEqualTo("dev_001");
        assertThat(IotdbPathBuilder.safe("a.b")).isEqualTo("a_b");
        assertThat(IotdbPathBuilder.safe("a b")).isEqualTo("a_b");
        assertThat(IotdbPathBuilder.safe("")).isEqualTo("_");
        assertThat(IotdbPathBuilder.safe(null)).isEqualTo("_");
    }

    @Test
    void safe_shouldGuardDigitLeadingName() {
        assertThat(IotdbPathBuilder.safe("3a")).isEqualTo("m_3a");
        assertThat(IotdbPathBuilder.safe("12")).isEqualTo("m_12");
    }

    @Test
    void modelAndDeviceNodes_shouldPrefixNodes() {
        assertThat(IotdbPathBuilder.modelNode("1")).isEqualTo("p1");
        assertThat(IotdbPathBuilder.deviceNode("3")).isEqualTo("d3");
    }
}
