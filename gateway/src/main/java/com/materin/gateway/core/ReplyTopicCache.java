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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * requestId → replyTopic 缓存（默认 5 分钟 TTL，惰性过期）：
 * 平台下行命令时登记应答主题，connector 回执时按 requestId 取回。
 * clock 可注入以便单测。
 */
public class ReplyTopicCache {

    private record Entry(String topic, String traceId, long expireAt) {
    }

    private final long ttlMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Entry> topics = new ConcurrentHashMap<>();

    public ReplyTopicCache(long ttlMillis) {
        this(ttlMillis, System::currentTimeMillis);
    }

    ReplyTopicCache(long ttlMillis, LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /** 登记命令应答主题；requestId/replyTopic 为空时忽略 */
    public void put(String requestId, String replyTopic) {
        put(requestId, replyTopic, null);
    }

    /** 登记命令应答主题并缓存该命令的 traceId（connector 异步回执时取回透传） */
    public void put(String requestId, String replyTopic, String traceId) {
        if (requestId == null || requestId.isBlank() || replyTopic == null || replyTopic.isBlank()) {
            return;
        }
        evictExpired();
        topics.put(requestId, new Entry(replyTopic, traceId, clock.getAsLong() + ttlMillis));
    }

    /** 取回该命令的 traceId；不存在或已过期返回 null */
    public String traceIdOf(String requestId) {
        if (requestId == null) {
            return null;
        }
        Entry entry = topics.get(requestId);
        if (entry == null) {
            return null;
        }
        if (clock.getAsLong() >= entry.expireAt()) {
            topics.remove(requestId);
            return null;
        }
        return entry.traceId();
    }

    /** 取回应答主题；不存在或已过期返回 null（过期条目顺带清除） */
    public String get(String requestId) {
        if (requestId == null) {
            return null;
        }
        Entry entry = topics.get(requestId);
        if (entry == null) {
            return null;
        }
        if (clock.getAsLong() >= entry.expireAt()) {
            topics.remove(requestId);
            return null;
        } else {
            return entry.topic();
        }
    }

    /** 当前有效条目数（先清除过期项） */
    public int size() {
        evictExpired();
        return topics.size();
    }

    private void evictExpired() {
        long now = clock.getAsLong();
        topics.values().removeIf(entry -> now >= entry.expireAt());
    }
}
