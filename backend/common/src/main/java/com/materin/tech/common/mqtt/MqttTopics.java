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
package com.materin.tech.common.mqtt;

/**
 * MQTT Topic 全量定义。
 * 统一前缀：materin/{productKey}/{deviceKey}/
 * 上行后缀：report（数据上报）、event（事件上报）、reply（指令响应）
 * 下行后缀：cmd（指令下发，含 cmd/{name} 细分）
 */
public final class MqttTopics {

    private MqttTopics() {
    }

    public static final String PREFIX = "materin/";

    /** 上行：数据上报 */
    public static final String UP_REPORT = "report";
    /** 上行：事件上报 */
    public static final String UP_EVENT = "event";
    /** 上行：指令响应 */
    public static final String UP_REPLY = "reply";
    /** 上行：DFX 健康指标上报（docs/dfx-monitoring-design.md §4.1） */
    public static final String UP_DFX = "dfx";
    /** 上行：OTA 升级进度上报 */
    public static final String UP_OTA = "ota";
    /** 下行：OTA 升级推送 */
    public static final String DOWN_OTA_PUSH = "ota_push";
    /** 下行：指令下发 */
    public static final String DOWN_CMD = "cmd";

    /** 平台通配（仅服务账号）：数据上报 */
    public static final String WILDCARD_REPORT = "materin/+/+/report";
    /** 平台通配（仅服务账号）：事件上报 */
    public static final String WILDCARD_EVENT = "materin/+/+/event";
    /** 平台通配（仅服务账号）：指令响应 */
    public static final String WILDCARD_REPLY = "materin/+/+/reply";
    /** 平台通配（仅服务账号）：DFX 健康指标上报 */
    public static final String WILDCARD_DFX = "materin/+/+/dfx";

    // ===== 网关主题族（对齐 ThingsBoard v1/gateway/* 语义，见 docs/architecture/07-gateway.md） =====

    /** 网关上行：子设备上线声明 {"device":"dk","name":".."} */
    public static final String GATEWAY_CONNECT = "materin/gateway/connect";
    /** 网关上行：子设备离线 {"device":"dk"} */
    public static final String GATEWAY_DISCONNECT = "materin/gateway/disconnect";
    /** 网关上行：子设备遥测 {"<dk>":[{"ts":毫秒,"values":{..}}]} */
    public static final String GATEWAY_TELEMETRY = "materin/gateway/telemetry";
    /** 网关上行：子设备属性 {"<dk>":{..}} */
    public static final String GATEWAY_ATTRIBUTES = "materin/gateway/attributes";
    /** 网关下行：平台→网关（子设备命令/属性推送） {"device":"dk","requestId":"..","data":{..}} */
    public static final String GATEWAY_DOWNLINK = "materin/gateway/downlink";
    /** 网关主题族通配（仅服务账号） */
    public static final String WILDCARD_GATEWAY = "materin/gateway/#";

    /**
     * 网关下行主题（按网关 deviceKey 分流，多网关互不串扰）：
     * 网关只能订阅自己的 downlink（ACL 精确匹配）。
     */
    public static String gatewayDownlinkTopic(String gatewayDeviceKey) {
        return GATEWAY_DOWNLINK + "/" + gatewayDeviceKey;
    }

    /** 是否网关主题族（materin/gateway/...） */
    public static boolean isGatewayTopic(String topic) {
        return topic != null && topic.startsWith(PREFIX + "gateway/");
    }


    /** 共享订阅组名（多实例负载均衡） */
    public static final String SHARE_GROUP = "materin-svc";

    /** 设备允许的上行后缀集合 */
    public static boolean isUpSuffix(String suffix) {
        return UP_REPORT.equals(suffix) || UP_EVENT.equals(suffix) || UP_REPLY.equals(suffix)
                || UP_DFX.equals(suffix) || UP_OTA.equals(suffix);
    }

    /** 设备允许的下行前缀判断 */
    public static boolean isDownPrefix(String suffix) {
        return DOWN_CMD.equals(suffix) || DOWN_OTA_PUSH.equals(suffix);
    }

    /**
     * 解析 topic：materin/{pk}/{dk}/{suffix}[/sub]
     * 返回 [productKey, deviceKey, suffix, rest]，不匹配返回 null。
     */
    public static String[] parse(String topic) {
        if (topic == null || !topic.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = topic.substring(PREFIX.length()).split("/");
        if (parts.length < 3) {
            return null;
        }
        String rest = parts.length > 4 ? String.join("/", java.util.Arrays.copyOfRange(parts, 4, parts.length)) : "";
        return new String[]{parts[0], parts[1], parts[2], rest};
    }
}
