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
package com.materin.gateway.mqtt;

/**
 * 网关协议主题常量（语义对齐 ThingsBoard v1/gateway/*）。
 * 独立程序不依赖 backend 模块，本地维护一份与平台 common MqttTopics 对齐的常量。
 */
public final class GatewayTopics {

    private GatewayTopics() {
    }

    public static final String PREFIX = "materin/";

    /** 网关上行：子设备上线声明 */
    public static final String CONNECT = "materin/gateway/connect";
    /** 网关上行：子设备离线 */
    public static final String DISCONNECT = "materin/gateway/disconnect";
    /** 网关上行：子设备遥测 */
    public static final String TELEMETRY = "materin/gateway/telemetry";
    /** 网关上行：子设备属性 */
    public static final String ATTRIBUTES = "materin/gateway/attributes";
    /** 网关下行：平台到网关的子设备命令（旧的全网关主题，保留兼容） */
    public static final String DOWNLINK = "materin/gateway/downlink";

    /** 按网关隔离的下行主题：materin/gateway/downlink/{gatewayDeviceKey}（ACL 限制只能订自己的） */
    public static String downlinkTopic(String gatewayDeviceKey) {
        return DOWNLINK + "/" + gatewayDeviceKey;
    }

    /** 拼接平台设备主题：materin/{pk}/{dk}/{suffix} */
    public static String deviceTopic(String pk, String dk, String suffix) {
        return PREFIX + pk + "/" + dk + "/" + suffix;
    }
}
