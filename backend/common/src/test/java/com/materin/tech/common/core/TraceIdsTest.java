/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.tech.common.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdsTest {

    @AfterEach
    void cleanMdc() {
        TraceIds.clearMdc();
    }

    @Test
    void normalizeStripsUnsafeCharacters() {
        assertThat(TraceIds.normalize("abc-123_XY.z\n\tinjected")).isEqualTo("abc-123_XY.zinjected");
    }

    @Test
    void normalizeTruncatesTo64Characters() {
        String longId = "a".repeat(100);
        assertThat(TraceIds.normalize(longId)).hasSize(64);
    }

    @Test
    void normalizeFallsBackToGeneratedWhenBlank() {
        assertThat(TraceIds.normalize(null)).isNotBlank().hasSize(32);
        assertThat(TraceIds.normalize("   ")).isNotBlank().hasSize(32);
        assertThat(TraceIds.normalize("###")).isNotBlank().hasSize(32);
    }

    @Test
    void setMdcStoresNormalizedValueAndClearRemovesIt() {
        String applied = TraceIds.setMdc("trace-001");
        assertThat(applied).isEqualTo("trace-001");
        assertThat(org.slf4j.MDC.get(TraceIds.MDC_KEY)).isEqualTo("trace-001");
        TraceIds.clearMdc();
        assertThat(org.slf4j.MDC.get(TraceIds.MDC_KEY)).isNull();
    }

    @Test
    void currentGeneratesWhenMdcEmptyAndReadsWhenPresent() {
        assertThat(TraceIds.current()).hasSize(32);
        org.slf4j.MDC.put(TraceIds.MDC_KEY, "existing-id");
        assertThat(TraceIds.current()).isEqualTo("existing-id");
    }
}
