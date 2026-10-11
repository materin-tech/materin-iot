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

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 全链路追踪 ID：
 * - HTTP 入站经 {@code TraceFilter} 取 X-Trace-Id 头（无则生成）写入 MDC；
 * - MQTT 上行在回调线程入口 setMdc、出口 clearMdc，报文携带 traceId 时以报文为准；
 * - 下行命令报文注入 traceId，设备/网关应答回传后恢复链路。
 * 载荷中的 traceId 为保留键，落库（Redis 最新值/时序库）前必须剔除。
 */
public final class TraceIds {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";
    /** 报文 JSON 保留键 */
    public static final String PAYLOAD_KEY = "traceId";

    private static final int MAX_LEN = 64;

    private TraceIds() {
    }

    /** 读当前 MDC，无则生成（不写入 MDC）。 */
    public static String current() {
        String v = MDC.get(MDC_KEY);
        return isBlank(v) ? generate() : v;
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 提取外部 traceId：仅保留安全字符、截断 64，非法/缺失则生成。 */
    public static String normalize(String candidate) {
        if (isBlank(candidate)) {
            return generate();
        }
        String v = candidate.replaceAll("[^A-Za-z0-9._-]", "");
        if (v.length() > MAX_LEN) {
            v = v.substring(0, MAX_LEN);
        }
        return v.isEmpty() ? generate() : v;
    }

    /** 设置 MDC 并返回实际生效的 traceId（MQTT 回调线程入口用）。 */
    public static String setMdc(String candidate) {
        String v = normalize(candidate);
        MDC.put(MDC_KEY, v);
        return v;
    }

    public static void clearMdc() {
        MDC.remove(MDC_KEY);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
