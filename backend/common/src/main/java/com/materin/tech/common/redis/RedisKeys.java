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

    /** 设备资料缓存 */
    public static final String DEVICE_INFO = "materin:device:info:";

    /** 命令回执信箱：requestId -> 设备应答（LPUSH/BLPOP 跨实例定向回传） */
    public static final String CMD_REPLY = "materin:cmd:reply:";
}
