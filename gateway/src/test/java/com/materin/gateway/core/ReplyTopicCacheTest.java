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
package com.materin.gateway.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ReplyTopicCache TTL 逻辑单测（注入假时钟，不依赖网络）。
 */
class ReplyTopicCacheTest {

    @Test
    @DisplayName("TTL 内 get 返回登记的 replyTopic")
    void returnsTopicWithinTtl() {
        long[] now = {1_000L};
        ReplyTopicCache cache = new ReplyTopicCache(5_000L, () -> now[0]);

        cache.put("req-1", "materin/pk/dk/reply");

        assertThat(cache.get("req-1")).isEqualTo("materin/pk/dk/reply");
    }

    @Test
	@DisplayName("TTL 过期后 get 返回 null 并清除条目")
    void expiresAfterTtl() {
        long[] now = {1_000L};
        ReplyTopicCache cache = new ReplyTopicCache(5_000L, () -> now[0]);

        cache.put("req-1", "materin/pk/dk/reply");
        now[0] += 5_001L;

        assertThat(cache.get("req-1")).isNull();
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("同一 requestId 重复 put 覆盖旧值并刷新 TTL")
    void overwritesExistingRequestId() {
        long[] now = {1_000L};
        ReplyTopicCache cache = new ReplyTopicCache(5_000L, () -> now[0]);

        cache.put("req-1", "topic-a");
        cache.put("req-1", "topic-b");

        assertThat(cache.get("req-1")).isEqualTo("topic-b");
    }

    @Test
    @DisplayName("三参 put 登记 traceId，traceIdOf 取回；两参 put 时为 null")
    void cachesTraceIdPerRequestId() {
        long[] now = {1_000L};
        ReplyTopicCache cache = new ReplyTopicCache(5_000L, () -> now[0]);

        cache.put("req-1", "materin/pk/dk/reply", "trace-abc");
        cache.put("req-2", "materin/pk/d2/reply");

        assertThat(cache.traceIdOf("req-1")).isEqualTo("trace-abc");
        assertThat(cache.traceIdOf("req-2")).isNull();
        assertThat(cache.traceIdOf("missing")).isNull();
    }

    @Test
    @DisplayName("traceIdOf 过期后返回 null 并清除条目")
    void traceIdExpiresWithEntry() {
        long[] now = {1_000L};
        ReplyTopicCache cache = new ReplyTopicCache(5_000L, () -> now[0]);

        cache.put("req-1", "topic-a", "trace-abc");
        now[0] += 5_001L;

        assertThat(cache.traceIdOf("req-1")).isNull();
    }

    @Test
    @DisplayName("size 不计入已过期条目")
    void sizeEvictsExpiredEntries() {
        long[] now = {1_000L};
        ReplyTopicCache cache = new ReplyTopicCache(5_000L, () -> { return now[0]; });
        cache.put("req-1", "topic-a");
        cache.put("req-2", "topic-b");
        now[0] += 5_001L;
        assertThat(cache.size()).isZero();
        cache.put("req-3", "topic-c");
        assertThat(cache.get("req-3")).isEqualTo("topic-c");
        assertThat(cache.size()).isEqualTo(1);
    }
}
