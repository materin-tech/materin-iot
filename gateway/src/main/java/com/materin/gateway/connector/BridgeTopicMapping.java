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
package com.materin.gateway.connector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * mqtt-bridge connector 的 topic 映射模型与解析：
 * topic 过滤器 + device-index（topic 段索引，0 起）→ 子设备 deviceKey，
 * 可选 command-topic 模板（{device} 占位符）用于下行命令。
 */
public record BridgeTopicMapping(String topicFilter, int deviceIndex, String commandTopic, String productKey) {

    /** 从 connectors 条目的 mappings 字段解析映射列表；为空抛 IllegalArgumentException */
    public static List<BridgeTopicMapping> parseList(Object raw) {
        List<BridgeTopicMapping> result = new ArrayList<>();
        for (Object item : Configs.listOf(raw)) {
            if (!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;
            String topic = Configs.stringOf(map, "topic", null);
            if (topic == null || topic.isBlank()) {
                continue;
            }
            result.add(new BridgeTopicMapping(
                    topic,
                    (int) Configs.longOf(map, "device-index", 0),
                    Configs.stringOf(map, "command-topic", null),
                    Configs.stringOf(map, "product-key", null)));
        }
        return result;
    }

    /** 从实际 topic 中按 device-index 提取 deviceKey；越界返回 null */
    public String extractDeviceKey(String topic) {
        String[] segments = topic.split("/");
        if (deviceIndex < 0 || deviceIndex >= segments.length) {
            return null;
        }
        return segments[deviceIndex];
    }

    /** 命令主题模板渲染：{device} 替换为 deviceKey */
    public String renderCommandTopic(String deviceKey) {
        return commandTopic == null ? null : commandTopic.replace("{device}", deviceKey);
    }
}
