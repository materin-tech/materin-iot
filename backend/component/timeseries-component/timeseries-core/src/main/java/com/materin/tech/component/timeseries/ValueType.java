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
package com.materin.tech.component.timeseries;

/** 时序列值类型（TSDB 无关，具体实现自行映射存储类型）。 */
public enum ValueType {

    LONG, DOUBLE, BOOLEAN, TEXT;

    /**
     * 物模型 dataType → 值类型（宽容解析）：
     * int/long → LONG；float/double/number → DOUBLE；boolean → BOOLEAN；其余 → TEXT。
     * dataType 可能是类型字符串，也可能是 {type: ...} 结构的 Map 序列化前缀，由调用方先行归一。
     */
    public static ValueType of(String raw) {
        if (raw == null || raw.isBlank()) {
            return TEXT;
        }
        return switch (raw.trim().toLowerCase()) {
            case "int", "integer", "long" -> LONG;
            case "float", "double", "number" -> DOUBLE;
            case "bool", "boolean" -> BOOLEAN;
            default -> TEXT;
        };
    }
}
