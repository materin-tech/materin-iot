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
