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

import org.slf4j.MDC;

import java.util.UUID;

/** 轻量链路追踪：与平台侧约定 MDC 键 / 报文保留键均为 "traceId"。 */
public final class TraceUtil {

    public static final String KEY = "traceId";

    private TraceUtil() {
    }

    /** 读当前 MDC，无则生成（不写入 MDC）。 */
    public static String current() {
        String v = MDC.get(KEY);
        return v == null || v.isBlank() ? UUID.randomUUID().toString().replace("-", "") : v;
    }

    /** 设置 MDC，空值兜底生成。 */
    public static void set(String v) {
        MDC.put(KEY, v == null || v.isBlank() ? current() : v);
    }

    public static void clear() {
        MDC.remove(KEY);
    }
}
