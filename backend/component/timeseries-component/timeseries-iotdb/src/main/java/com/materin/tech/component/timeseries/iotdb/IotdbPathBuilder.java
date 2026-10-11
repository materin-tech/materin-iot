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
package com.materin.tech.component.timeseries.iotdb;

/**
 * IoTDB 树模型路径构造：{database}.p{modelId}.d{id}.{measurement}。
 * 设备维度 = d 节点；属性/事件/方法全部拍平为其下测量列（2.0 测量名不允许点号）。
 * 全部段做安全化（仅 [0-9a-zA-Z_]），杜绝路径注入。
 */
public final class IotdbPathBuilder {

    private IotdbPathBuilder() {
    }

    /** 模型节点：p + 安全化 modelId（如 p1）；p 前缀已保证字母开头，无需数字守卫。 */
    public static String modelNode(String modelId) {
        return "p" + sanitize(modelId);
    }

    /** 设备节点：d + 安全化业务主键（如 d3）；d 前缀已保证字母开头，无需数字守卫。 */
    public static String deviceNode(String id) {
        return "d" + sanitize(id);
    }

    /**
     * 测量名安全化：非法字符替换为下划线；数字开头补前缀（IoTDB 裸标识不允许数字开头）。
     * 注意：替换可能引入碰撞（a-1 与 a_1），属可接受的收敛策略。
     */
    public static String safe(String raw) {
        String s = sanitize(raw);
        if (Character.isDigit(s.charAt(0))) {
            s = "m_" + s;
        }
        return s;
    }

    /** 仅替换非法字符为下划线，不补数字前缀（供自带字母前缀的节点使用）。 */
    private static String sanitize(String raw) {
        String s = raw == null ? "" : raw.trim().replaceAll("[^0-9a-zA-Z_]", "_");
        return s.isEmpty() ? "_" : s;
    }
}
