# DFX 健康指标接入指南（设备开发者版）

> 面向对象：设备/网关固件开发者。协议细节与平台内部实现见
> `docs/dfx-monitoring-design.md`，本文只讲"怎么接、怎么鉴权、怎么调试"。

## 1. 鉴权：一套凭证走天下

**DFX 不引入任何新的鉴权体系。** 三种通道的凭证关系：

| 通道 | 鉴权方式 | 凭证来源 |
|---|---|---|
| MQTT | 连接时 username=deviceKey, password=secret | 设备详情页的 设备标识 + 设备密钥 |
| HTTP | 请求体 deviceKey + secret 字段 | 同一对 deviceKey/secret，**零额外注册** |
| SNMP | 标准 community（v2c） | 运维在平台"设备运维-轮询目标"里配置，设备侧即 snmpd 的 rocommunity |

原则：
- 已能连 MQTT 的设备，同一对凭证直接可调 HTTP 上报，无需任何申请/开通。
- HTTP 鉴权与 MQTT 认证走平台内**同一个凭证源**（device.secret），改密即全通道生效。
- 鉴权失败统一返回 401；凭证错误不会锁定（设备场景无暴力破解风险面，
  请勿在公网明文暴露端口，生产建议 TLS）。

## 2. 指标怎么报（指标字典见设计文档 §3）

统一 Payload：**扁平 JSON，键 = 指标名，值 = 数值**。三条通道 Payload 完全一致
（MQTT 是裸指标 JSON；HTTP 多一层信封）。

```json
{"cpu_usage_pct": 37.5, "mem_usage_pct": 62.1, "disk_usage_pct": 55.0, "uptime_s": 86400}
```

规则：
- 采集周期建议 60s，最短 10s；不携带时间戳则默认服务器接收时间。
- 离线缓存补报必须携带 `time`（epoch 毫秒），否则按上报时刻入库。
- 未知指标名平台不拒绝，按 TEXT 兜底落库（仅展示，不参与阈值告警）。
- 平台不做限流，但请勿高于 10s 周期——高频对设备无益（复用 Memfault
  心跳口径：预聚合、周期上报，不做原始数据流）。

## 3. 三通道接入案例（可直接抄）

### 3.1 MQTT 通道（推荐，常连设备）

设备连上 EMQX 后发布到自身 namespace：

```bash
mosquitto_pub -h <EMQX地址> -p 1883 \
  -u "$DEVICE_KEY" -P "$DEVICE_SECRET" \
  -t "materin/$PRODUCT_KEY/$DEVICE_KEY/dfx" \
  -m '{"cpu_usage_pct":37.5,"mem_usage_pct":62.1,"uptime_s":86400}'
```

Python (paho-mqtt) 版：

```python
import json, time, paho.mqtt.client as mqtt

c = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
c.username_pw_set(DEVICE_KEY, DEVICE_SECRET)
c.connect(EMQX_HOST, 1883)
c.loop_start()
while True:
    payload = json.dumps({
        "cpu_usage_pct": psutil.cpu_percent(),
        "mem_usage_pct": psutil.virtual_memory().percent,
        "uptime_s": int(time.time() - psutil.boot_time()),
    })
    c.publish(f"materin/{PRODUCT_KEY}/{DEVICE_KEY}/dfx", payload)
    time.sleep(60)
```

### 3.2 HTTP 通道（无长连接 / 网关代理）

单设备：

```bash
curl -X POST http://<平台地址>:8080/api/v1/device/dfx/report \
  -H 'content-type: application/json' \
  -d '{
    "deviceKey": "dfx-001",
    "secret": "<设备密钥>",
    "metrics": {"cpu_usage_pct": 37.5, "mem_usage_pct": 62.1}
  }'
```

网关代理批量（网关用自身凭证，代理上报其下设备）：

```bash
curl -X POST http://<平台地址>:8080/api/v1/device/dfx/report/batch \
  -H 'content-type: application/json' \
  -d '{
    "deviceKey": "gw-001",
    "secret": "<网关密钥>",
    "devices": [
      {"deviceKey": "th-001", "metrics": {"cpu_usage_pct": 41.0}},
      {"deviceKey": "th-002", "metrics": {"cpu_usage_pct": 38.2}}
    ]
  }'
```

### 3.3 SNMP 通道（Linux 设备零代码）

```bash
apt install snmpd && cat > /etc/snmp/snmpd.conf <<CONF
agentaddress udp:0.0.0.0:161
rocommunity public
CONF
systemctl restart snmpd
```

然后让运维在平台"设备运维 → SNMP 轮询目标"里添加该设备的 IP，
无需设备侧写任何代码。完整样例见 `docs/dfx/snmpd.conf.sample`。

## 4. 参考采集器

`tools/dfx-agent/dfx-agent.sh`：Linux 下 CPU/内存/磁盘/负载采集脚本，
`MODE=mqtt|http` 切换通道，开箱即用（约 60 行 bash，可作为固件集成参考）。

## 5. 应答与错误码

| 场景 | 应答 |
|---|---|
| HTTP 成功 | `{"code":0,"message":"success","data":{"accepted":<指标数>}}` |
| 凭证缺失/格式错误 | 400 `deviceKey/secret 不能为空` |
| 凭证错误 | 401 `设备凭证校验失败` |
| 未知指标 | 不拒绝（TEXT 兜底），`accepted` 仍计数 |
| MQTT/SNMP | 无应答（fire-and-forget，平台侧日志可查） |

## 6. 最佳实践

1. **心跳兜底**：即使无资源指标，也周期上报 `{"heartbeat":1}`，
   可作为应用层存活信号（在线判定仍以 MQTT 连接层为准）。
2. **指标预聚合**：弱网设备在设备侧按周期聚合（counter 类指标区间末重置），
   不要传原始采样流。
3. **失败重试**：HTTP 失败指数退避重试（如 30s/2min/5min），不要 busy retry。
4. **时间戳**：设备有时钟就用 `time` 字段；没有就省略，平台按接收时刻入库。
5. **告警订阅**：阈值告警出现在平台"设备管理 → 事件告警"页，
   规则由运维在"设备运维"页配置（阈值 + 比较符 + 级别 + 抑制窗口）。
