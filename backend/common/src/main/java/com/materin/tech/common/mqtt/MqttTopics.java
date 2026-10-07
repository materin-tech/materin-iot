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

    /** 共享订阅组名（多实例负载均衡） */
    public static final String SHARE_GROUP = "materin-svc";

    /** 设备允许的上行后缀集合 */
    public static boolean isUpSuffix(String suffix) {
        return UP_REPORT.equals(suffix) || UP_EVENT.equals(suffix) || UP_REPLY.equals(suffix)
                || UP_DFX.equals(suffix);
    }

    /** 设备允许的下行前缀判断 */
    public static boolean isDownPrefix(String suffix) {
        return DOWN_CMD.equals(suffix);
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
