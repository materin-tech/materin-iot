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
package com.materin.gateway.connector.opcua;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.materin.gateway.connector.Configs;

/**
 * opcua connector 配置模型与解析：nodes 列表（node-id → 遥测键 name）。
 */
public record OpcUaNodeConfig(String endpointUrl, String deviceKey, long pollPeriodMs,
        List<NodeMapping> nodes) {

    private static final long DEFAULT_POLL_MS = 5000L;

    /** 从 connectors 条目解析；endpoint-url / nodes 缺失时抛 IllegalArgumentException */
    public static OpcUaNodeConfig parse(Map<String, Object> cfg, String id) {
        String endpointUrl = Configs.stringOf(cfg, "endpoint-url", null);
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("opcua connector 需要配置 endpoint-url");
        }
        List<NodeMapping> nodes = parseNodes(cfg.get("nodes"));
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("opcua connector 需要至少一条 nodes 配置");
        }
        return new OpcUaNodeConfig(endpointUrl,
                Configs.stringOf(cfg, "device-key", id),
                Configs.longOf(cfg, "poll-period-ms", DEFAULT_POLL_MS),
                List.copyOf(nodes));
    }

    private static List<NodeMapping> parseNodes(Object raw) {
        List<NodeMapping> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> map = Configs.castMap(item);
                String nodeId = Configs.stringOf(map, "node-id", null);
                if (nodeId == null || nodeId.isBlank()) {
                    continue;
                }
                result.add(new NodeMapping(nodeId, Configs.stringOf(map, "name", nodeId)));
            }
        }
        return result;
    }

    /** 节点映射：nodeId ↔ 遥测键 */
    public record NodeMapping(String nodeId, String name) {
    }
}
