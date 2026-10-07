# 设备 DFX 健康监控 — 接入标准规范与选型设计

> 版本：v1.0（2026-10-07） · 状态：已按本规范实现 SNMP + MQTT + HTTP 三通道与设备运维页
> DFX = Design for X（可靠性/可运维性设计），本规范定义设备侧运行健康指标（CPU/内存/磁盘/网络/在线状态等）
> 如何以统一口径接入平台，实现监控、展示与阈值告警的闭环。

## 1. 顶层设计

### 1.1 架构：统一指标模型 + 多协议接入适配

设备形态多样（Linux 网关、嵌入式 MCU、网络设备、无法长连的批量网关），单一通道无法覆盖。
本方案把「协议接入」与「指标消费」解耦：

```mermaid
flowchart LR
    subgraph 接入适配层（协议各表其表）
        S1[SNMP 轮询<br/>平台拉取 v2c]
        S2[MQTT 上报<br/>materin/+/+/dfx]
        S3[HTTP 推送<br/>POST /device/dfx/report]
        S4[LwM2M 观察二期<br/>Eclipse Leshan]
    end
    subgraph 统一指标管道（协议无关）
        I[DfxIngestService<br/>校验/标准化]
        T[IoTDB 时序存储<br/>dfx_ 前缀列]
        R[Redis 最新值<br/>materin:device:dfx:{dk}]
        A[阈值告警评估<br/>dfx_alert_rule → device_alert]
    end
    V[设备运维页<br/>/device/monitor]
    S1 & S2 & S3 -.-> I --> T & R & A
    T & R & A --> V
```

**核心约束：任何接入协议产生的指标，必须映射到第 3 节的统一指标字典后进入管道；
管道内（校验、存储、告警、展示）不感知协议。** 新增协议（如 LwM2M）只需实现一个适配器。

### 1.2 选型结论与理由

| 维度 | 选型 | 理由 | 落选方案 |
|---|---|---|---|
| v1 协议一 | **SNMP v2c**（平台轮询） | CPU/内存监控的典型对象是 Linux 网关/主机，`snmpd` 是 Linux 发行版预装标准组件（UCD-SNMP-MIB + HOST-RESOURCES-MIB），设备侧几乎零开发；snmp4j 成熟稳定 | Prometheus 拉模型（需改设备暴露 exporter，与 MQTT 架构双轨） |
| v1 协议二 | **MQTT 上报**（`dfx` 后缀） | 复用现有 MQTT 接入/鉴权/ACL/共享订阅全链路，常连设备的默认通道，增量成本最低 | 单独建 broker/topic 体系（破坏 MqttTopics 统一约定） |
| v1 协议三 | **HTTP 推送**（设备 secret 鉴权） | 覆盖无法维持长连接的场景与批量网关代理上报（一次 POST 带多设备数据），实现即一个 REST 端点 | WebSocket/GRPC（重量级，无必要） |
| 时序存储 | 复用 IoTDB（timeseries-component 抽象） | 设备属性/事件/方法已同构落库，`dfx_` 前缀列同一设备维度，聚合查询直接复用 | 独立 TSDB（引入新组件无收益） |
| 告警 | 复用 `device_alert` 表 + 新增结构化规则表 `dfx_alert_rule` | 告警处理 UI 已存在；规则从自由文本升级为结构化条件 | 复用 device_rule（自由文本无法执行） |
| 二期协议 | LwM2M（Eclipse Leshan 嵌入后端） | 受限 MCU 的 OMA 标准，IPSO 对象映射见 §6，等一期管道验证后再接入 | — |

### 1.3 设备侧参考采集器选型

| 场景 | 参考实现 | 位置 |
|---|---|---|
| Linux（SNMP 路线） | 标准 `snmpd` 配置样例（零代码，装包即用） | `docs/dfx/snmpd.conf.sample` |
| Linux/嵌入式（主动上报） | Shell 参考采集器（CPU/内存/负载，MQTT 或 HTTP 模式可切换） | `tools/dfx-agent/dfx-agent.sh` |

## 2. 在线状态口径（DFX 基础）

- 设备任意上行（report/event/reply/**dfx**）刷新 Redis 在线标记（滑动 TTL，默认 300s）。
- 平台每 60s 巡检：有在线标记而 `device.status != 1` → 置 1；无标记而 `status != 2` → 置 2
  并产生 `warn` 级离线告警（状态迁移时才产生，不重复）。这补齐了"status 字段无人写"的历史缺口。
- `dfx.uptime_s`、`dfx.heartbeat`（自定义 heartbeat 指标）可作为设备应用层心跳的补充信号，
  但**在线判定的唯一权威是 MQTT 连接层消息**（SNMP 轮询结果不改变在线状态——轮询可达仅证明网络通，不证明设备 MQTT 在线）。

## 3. 统一指标字典（协议无关）

命名规范：`snake_case`，`_pct` 后缀表示 0~100 百分比，`_s` 表示秒，`_kbps` 表示千比特每秒。
数值型指标可参与告警与聚合；非数值指标按 TEXT 落库仅作展示。

| 指标 | 类型 | 单位 | 来源协议 | 说明 |
|---|---|---|---|---|
| `cpu_usage_pct` | gauge | % | 全部 | CPU 使用率（SNMP=100-ssCpuIdle） |
| `mem_usage_pct` | gauge | % | 全部 | 内存使用率（SNMP=(TotalReal-AvailReal)/TotalReal） |
| `swap_usage_pct` | gauge | % | 全部 | 交换分区使用率（可选） |
| `disk_usage_pct` | gauge | % | 全部 | 根分区使用率 |
| `load_1m` | gauge | - | SNMP/agent | 1 分钟负载 |
| `net_rx_kbps` / `net_tx_kbps` | gauge | kbps | agent | 网络吞吐（可选） |
| `uptime_s` | counter | s | 全部 | 本次运行时长 |
| `heartbeat` | gauge | - | 全部 | 固定为 1 的心跳标记 |
| 其他自定义 | 自动 | - | 全部 | 未知键按 TEXT 兜底落库（与遥测口径一致） |

指标类型学（参考 Memfault fleet heartbeat 方法论）：**gauge**（瞬时值）、**counter**（区间累计，
由设备侧重置）。采集周期建议 60s，最短 10s；平台侧 SNMP 轮询间隔按目标配置（默认 60s）。

## 4. 接入协议规范

### 4.1 MQTT 通道（推荐给常连设备）

- 主题：`materin/{productKey}/{deviceKey}/dfx`（上行，QoS 0/1，设备走自身 namespace ACL，自动放行）
- Payload：扁平 JSON，键 = 指标名，值 = 数值/字符串：

```json
{"cpu_usage_pct": 37.5, "mem_usage_pct": 62.1, "disk_usage_pct": 55.0, "uptime_s": 86400}
```

- 平台以共享订阅 `$share/materin-svc/materin/+/+/dfx` 消费，多实例负载均衡。
- 鉴权/ACL：与 report/event/reply 完全一致（deviceKey/secret 连接，仅限自身 namespace）。

### 4.2 HTTP 通道（无法长连或网关代理批量上报）

- 端点：`POST /api/v1/device/dfx/report`（无需 JWT，设备 secret 鉴权）
- 请求体（单设备）：

```json
{
  "deviceKey": "th-001",
  "secret": "<device.secret>",
  "time": 1730000000000,
  "metrics": {"cpu_usage_pct": 37.5, "mem_usage_pct": 62.1}
}
```

- 批量（网关代理）：`{"deviceKey":"gw-001","secret":"...","devices":[{"deviceKey":"th-001","metrics":{...}}, ...]}`
  ——gw 自身凭证鉴权，代理上报其下设备（本期仅支持同产品下设备）。
- 应答：`{"code":0,...}` 统一信封；凭证错误 401、校验失败 400。
- `time` 可省略（默认服务器当前时间）；频率与 MQTT 相同（建议 60s，最短 10s，平台不做限流一期）。

### 4.3 SNMP 通道（Linux 网关/主机，平台轮询）

- 平台为 SNMP manager（snmp4j），对启用 SNMP 的设备按配置间隔轮询 v2c GET。
- 目标配置（`dfx_snmp_target` 表，或设备运维页配置）：`host, port=161, version=v2c, community, interval_seconds=60`。
- OID 映射（UCD-SNMP-MIB / SNMPv2-MIB）：

| 指标 | OID |
|---|---|
| `cpu_usage_pct` | `1.3.6.1.4.1.2021.11.11.0`（ssCpuIdle，取 100-idle） |
| `mem_usage_pct` | `1.3.6.1.4.1.2021.4.5.0` / `.4.6.0`（memTotalReal/memAvailReal，KB） |
| `load_1m` | `1.3.6.1.4.1.2021.10.1.3.1`（laLoad.1） |
| `uptime_s` | `1.3.6.1.2.1.1.3.0`（sysUpTime，厘秒→秒） |

- 磁盘/网络等扩展指标走 agent 的 MQTT/HTTP 通道（host-resources 表类对象二期支持）。
- 设备侧参考：`docs/dfx/snmpd.conf.sample`（`apt install snmpd` / `brew install net-snmp` 后套用）。
- **SNMP 通道语义**：轮询成功产生指标数据；轮询失败仅记日志（连续失败可配置告警，二期），
  不参与 MQTT 在线判定（见 §2）。

### 4.4 通用语义

- 时间戳：协议未携带时用平台接收时间；携带则必须是 epoch 毫秒。
- 幂等：平台不去重，设备应按采集周期上报，重复数据的收敛由聚合查询天然兜底。
- 降级：时序库不可用时指标管道降级（最新值仍进 Redis，历史缺失），**不阻断设备上行链路**。

## 5. 阈值告警

- 规则表 `dfx_alert_rule`：`(device_id, metric, comparator[gt/lt], threshold, level[info/warn/error], enabled, suppress_seconds)`。
- 评估时机：任一通道指标入库后同步评估该设备的启用规则。
- 告警落 `device_alert`（content 如 `CPU 使用率 95.2% 超过阈值 90%（连续抑制 600s）`）。
- **抑制窗口**：同一 (device, metric) 触发告警后 `suppress_seconds`（默认 600s）内不重复产生；
  窗口过后仍越限则再次告警。平台重启后抑制状态清零（一期可接受）。

## 6. 二期：LwM2M（预留）

平台嵌入 Eclipse Leshan Server（CoAP :5683），IPSO 对象映射：

| LwM2M/IPSO 对象 | 资源 | 统一指标 |
|---|---|---|
| Object 3 (Device) | Available Memory / Error Code / Unix Time | `mem_usage_pct`（换算）等 |
| Object 4 (Connectivity) | Signal Strength / Link Quality | 自定义 `net_*` |
| Object 3313?/自定义 33000+ | CPU Load | `cpu_usage_pct` |

适配器实现 `DfxSink` 同一 SPI 即可接入管道（无需改动存储/告警/展示）。

## 7. 开发者文档与实现落点

**设备开发者接入说明与案例：`docs/dfx-developer-guide.md`**
（鉴权口径：一套设备凭证走三通道，零新增鉴权体系；含 MQTT/HTTP/SNMP
可直抄案例，均已实测）。

实现落点（本期）

| 层 | 文件/位置 |
|---|---|
| 标准通道 | `MqttTopics.UP_DFX` + `TelemetryHandler.handle(dfx 分支)` + `MqttProtocol` 共享订阅 |
| HTTP 通道 | `DeviceDfxController` `POST /device/dfx/report`（deviceKey+secret 鉴权） |
| SNMP 通道 | `network-component/network-snmp`（snmp4j 轮询，`materin.dfx.snmp.enabled` 开关） |
| 管道 | `device-component/dfx/DfxIngestService`（SPI `DfxSink`，common/spi 定义） |
| 存储 | `dfx_` 前缀列，`root.materin.p{pid}.d{did}.dfx_*`；Redis `materin:device:dfx:{dk}` |
| 告警 | `dfx_alert_rule` 评估 → `device_alert`；`DfxOfflineDetector` 每 60s 状态巡检 |
| 展示 | 前端 `views/device/monitor.vue` + 菜单 `/device/monitor` |
| 参考采集器 | `tools/dfx-agent/` + `docs/dfx/snmpd.conf.sample` |
