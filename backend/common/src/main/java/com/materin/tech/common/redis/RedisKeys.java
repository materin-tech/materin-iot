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
package com.materin.tech.common.redis;

/** Redis key 规划（全平台统一前缀 materin:）。 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** opaque refreshToken -> 用户上下文 JSON */
    public static final String AUTH_REFRESH = "materin:auth:refresh:";

    /** 被吊销的 accessToken jti */
    public static final String AUTH_BLACKLIST = "materin:auth:bl:";

    /** 登录失败计数 */
    public static final String AUTH_FAIL = "materin:auth:fail:";

    /** 设备在线标记（滑动 TTL） */
    public static final String DEVICE_ONLINE = "materin:device:online:";

    /** 遥测最新值 HASH */
    public static final String DEVICE_TELEMETRY = "materin:device:telemetry:";

    /** 设备 DFX 健康指标最新值 HASH */
    public static final String DEVICE_DFX = "materin:device:dfx:";

    /** DFX 规则告警抑制窗口：{deviceId}:{ruleId} -> 首次触发时间戳（SETNX+TTL） */
    public static final String DFX_SUPPRESS = "materin:dfx:suppress:";

    /** 设备资料缓存 */
    public static final String DEVICE_INFO = "materin:device:info:";

    /** 命令回执信箱：requestId -> 设备应答（LPUSH/BLPOP 跨实例定向回传） */
    public static final String CMD_REPLY = "materin:cmd:reply:";
}
