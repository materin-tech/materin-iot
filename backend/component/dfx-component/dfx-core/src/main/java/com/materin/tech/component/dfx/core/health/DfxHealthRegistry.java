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
package com.materin.tech.component.dfx.core.health;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** 管道侧统计（按来源计数），供健康接口展示各通道最近接入情况。 */
@Component
public class DfxHealthRegistry {

    public record SourceStat(long count, long lastIngestAt) {
    }

    private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastAt = new ConcurrentHashMap<>();

    public void recordIngest(String source) {
        counts.computeIfAbsent(source, k -> new LongAdder()).increment();
        lastAt.computeIfAbsent(source, k -> new AtomicLong()).set(System.currentTimeMillis());
    }

    public Map<String, SourceStat> sourceStats() {
        Map<String, SourceStat> result = new ConcurrentHashMap<>();
        counts.forEach((k, v) -> result.put(k,
                new SourceStat(v.sum(), lastAt.containsKey(k) ? lastAt.get(k).get() : 0L)));
        return result;
    }
}
