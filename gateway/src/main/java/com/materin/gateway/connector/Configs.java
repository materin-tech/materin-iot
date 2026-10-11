/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated typed files (the "Software"), to deal
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
package com.materin.gateway.connector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * connector 配置解析小工具：从 yml 的 List<Map<String,Object>> 中安全取值，
 * 解析失败降级为默认值，绝不抛异常。
 */
public final class Configs {

    private Configs() {
    }

    /**
     * 取列表值：兼容两种绑定形态——原生 List，以及 Spring 绑定 List 进 Object 字段时
     * 产生的索引 Map（{0={..},1={..}}，按数字键排序取 values）。
     */
    public static List<Object> listOf(Object raw) {
        if (raw instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(item);
            }
            return out;
        }
        if (raw instanceof Map<?, ?> indexMap) {
            return indexMap.entrySet().stream()
                    .sorted(java.util.Comparator.comparingLong(e -> parseIndex(e.getKey())))
                    .map(e -> (Object) e.getValue())
                    .toList();
        }
        return List.of();
    }

    private static long parseIndex(Object key) {
        try {
            return Long.parseLong(String.valueOf(key));
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    /** 安全取字符串 */
    public static String stringOf(Map<String, Object> cfg, String key, String defaultValue) {
        Object value = cfg.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    /** 安全取 long（Number 或数字字符串），失败回退默认值 */
    public static long longOf(Map<String, Object> cfg, String key, long defaultValue) {
        Object value = cfg.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /** 安全取 double */
    public static double doubleOf(Map<String, Object> cfg, String key, double defaultValue) {
        Object value = cfg.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /** 任意 Map（可能是 Map<?,?>）安全转为 Map<String,Object>（键取 toString） */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> castMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
