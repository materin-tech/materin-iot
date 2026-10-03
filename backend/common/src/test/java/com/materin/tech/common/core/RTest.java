package com.materin.tech.common.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RTest {

    @Test
    void okWrapsDataWithSuccessCode() {
        R<String> r = R.ok("payload");
        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isEqualTo("payload");
    }

    @Test
    void failCarriesCodeAndMessageWithoutData() {
        R<Void> r = R.fail(400, "bad");
        assertThat(r.getCode()).isEqualTo(400);
        assertThat(r.getMessage()).isEqualTo("bad");
        assertThat(r.getData()).isNull();
    }
}
