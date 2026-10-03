# MQTT 接入设计（EMQX + Spring Boot 无状态服务）

## 1. Topic 全量定义

统一前缀：`materin/{productKey}/{deviceKey}/`

| Topic | 方向 | 用途 | QoS |
|---|---|---|---|
| `materin/{pk}/{dk}/report` | 设备→平台 | 数据上报（遥测/属性） | 1 |
| `materin/{pk}/{dk}/event` | 设备→平台 | 事件/告警上报 | 1 |
| `materin/{pk}/{dk}/reply` | 设备→平台 | 指令执行结果响应 | 1 |
| `materin/{pk}/{dk}/cmd` | 平台→设备 | 指令下发（payload 携带指令名） | 1 |
| `materin/{pk}/{dk}/cmd/{name}` | 平台→设备 | 指令细分（预留） | 1 |

平台侧（仅服务账号 `materin-svc-*`）：`materin/+/+/report|event|reply` 通配订阅。

## 2. 鉴权与授权（EMQX HTTP 回调 → backend）

- 认证（EMQX authenticator = password_based:http）：
  EMQX 收到 CONNECT → POST `/api/v1/mqtt/auth`
  `{username, password}` → backend 校验：
  - 设备：username=deviceKey，password=device.secret（MySQL）→ `{"result":"allow"|"deny"}`
  - 服务账号：username=`materin-svc-*`，password=配置的服务密钥
- 授权（EMQX authorizer = http）：
  每次 PUB/SUB → POST `/api/v1/mqtt/acl`
  `{username, topic, action}` → 校验：
  - topic 中 deviceKey 必须 == username
  - topic 中 productKey 必须等于该设备所属产品
  - publish 后缀 ∈ {report, event, reply}；subscribe 后缀 ∈ {cmd, cmd/...}
  - 越权 → deny（EMQX 断开/拒绝订阅）
- 鉴权不通过：连接直接被 EMQX 拒绝（CONNACK 5）。

## 3. 无状态消费（水平扩展）

- backend 通过 HiveMQ client 以**共享订阅**消费：
  `$share/materin-svc/materin/+/+/report`（event/reply 同理）
  → 多实例部署时 EMQX 自动按组负载均衡，实例本身不保存任何会话状态。
- 服务端下行发布：直接向 `materin/{pk}/{dk}/cmd` 发布。

## 4. Redis 状态分层（服务无状态，状态全部外置）

| Key | 结构 | 用途 |
|---|---|---|
| `materin:auth:refresh:{token}` | string(JSON) TTL 7d | opaque refreshToken，可吊销、一次性轮换 |
| `materin:auth:bl:{jti}` | string TTL=剩余寿命 | logout 吊销 accessToken |
| `materin:auth:fail:{username}` | counter TTL 10m | 登录失败 5 次锁定 |
| `materin:device:online:{dk}` | string TTL 300s 滑动 | 设备在线状态（每次上报刷新） |
| `materin:device:telemetry:{dk}` | hash TTL 7d | 遥测最新值（dashboard 秒查） |
| `materin:device:info:{dk}` | string | 设备资料缓存（预留） |

## 5. 下行命令的跨实例回传（观察者问题解法）

问题：无状态多副本下，reply 经共享订阅随机到达任一实例，而 HTTP 请求阻塞在发起实例。

解法：**requestId + Redis 信箱（LPUSH/BLPOP）**，EMQX 组件保证"必达"，Redis 保证"定向"：

```
实例A: POST /device/1/cmd → 生成 requestId → PUBLISH cmd(payload 带 requestId)
实例A: BLPOP materin:cmd:reply:{requestId} (超时=失败)
任意实例B(共享订阅收到 reply): 解析 requestId → LPUSH materin:cmd:reply:{requestId} + EXPIRE 60s
实例A: BLPOP 立即返回 → HTTP 响应
```

- 设备协议约定：reply payload 必须原样带回 cmd 中的 requestId。
- 发布前校验 Redis 在线状态，离线直接 409 拒绝。
- 实例不持有等待会话：实例宕机后 BLPOP 断开，信箱 key 60s TTL 自动清理。
- 接口：POST /api/v1/device/{id}/cmd（同步等待，默认 10s 超时返回 504）。
- 已否决方案：EMQX 规则引擎/Webhook 定向回传（对多实例仍是 LB 语义，路由信息只能在应用层）；设备端动态 replyTo（把路由复杂度压给设备端）。

## 6. 双 EMQX 集成（开发者接入）

- **EMQX-2**（开发者接入）与 EMQX-1 消息互通，通过 EMQX 5.8 集成组件：**connector + action(sink) + rule**（旧 bridges API 在 5.8 已不兼容）。
- 命名空间隔离：开发者侧全部在 `open/` 前缀下。
- 上行（EMQX-1→EMQX-2）：规则 `up_report_to_emqx2` / `up_event_to_emqx2` / `up_reply_to_emqx2`
  SQL：`SELECT concat('open/', topic) AS topic, payload FROM "materin/+/+/report|event"`，
  sink `sink_to_emqx2`（topic=${topic}, payload=${payload}）。
- 下行（EMQX-2→EMQX-1）：规则 `down_cmd_to_emqx1`
  SQL：`SELECT regex_replace(topic, '^open/', '') AS topic, payload FROM "open/materin/+/+/cmd"`，
  sink `sink_to_emqx1`（连接 EMQX-1 用服务账号 materin-svc-emqx2-bridge，过 HTTP 认证）。
- 配置脚本：`deploy/compose/emqx-configure.sh`（幂等一键脚本，见配置手册）。
- **AK/SK 鉴权（已落地）**：EMQX-2 authenticator/authorizer 回调 backend `/api/v1/mqtt/auth|acl`。
  应用体系（system-openapi 模块）：open_app（AK=mk+16hex，SK=32byte hex，仅创建/重置时完整返回一次）、
  open_api（从 /v3/api-docs 同步的接口清单）、open_app_api（应用-接口勾选授权）。
  MQTT 校验分支：username=AK → password==SK → allow（错误 SK/禁用应用 deny）；
  ACL：AK 用户仅允许 open/ 命名空间（pub/sub），内部 materin/ 命名空间 deny（实测未泄露）。
  匿名连接被拒（CONNACK 5）。前端「开发者中心」：应用管理 + 接口授权（swagger 同步 55 接口、勾选授权、回显）。
- **闭环验证**（`deploy/compose/e2e-developer-cmd.sh`，5/5 PASS）：
  开发者(EMQX-2)发 cmd(requestId=dev-e2e-001) → 集成管道 → 模拟设备(EMQX-1)收到并执行
  → reply(requestId 原样回带) → 开发者(EMQX-2)收到，断言 requestId/执行结果/指令 echo 三项一致；
  平台 HTTP 链路（/device/{id}/cmd）回归通过；report 对开发者透传通过。
  协议约定：开发者侧 cmd **必须自带 requestId**（设备原样回带），平台内部链路由平台生成。
- 踩坑：EMQX SQL `regex_replace(subject, regex, replacement)` 参数顺序（subject 在前）；actions API 的 mqtt action 参数名是 `payload` 不是 `payload_template`；GET 资源路径要带 type 前缀（/actions/mqtt:xxx）。

## 7. 容量与并发治理（防 Redis 瓶颈/大 key）

| 风险 | 治理 |
|---|---|
| 大 key | 回执 > `materin.command.big-payload-threshold`（默认 16KB）→ 落 `device_command_log` 表，Redis 只放 `{ref:"db",logId}` 引用，发起方 `resolve()` 透明解引用 |
| 等待并发 | `materin.command.max-concurrent-waits`（默认 100）信号量限制 BLPOP 并发，超限 429 快速失败；长任务应改异步模式（提交返回 requestId + 轮询） |
| 计数竞态 | 产品 deviceCount 用 DB 原子自增 `device_count = device_count + 1`（多实例安全，无需分布式锁） |
| 遥测热点 | 最新值 hash 高频写 → 最新值语义幂等，可实例内本地节流；DB last_online 已按 60s 节流 |
| key 泄漏 | 信箱 TTL 60s 自清理；BLPOP 超时后迟到的 reply 由 TTL 兜底回收 |

容量口径：命令/回执属控制面（<1k/s），Redis 小值 10w+ ops/s，信箱模式不构成瓶颈；数据面（遥测洪峰）走时序库路径，Redis 只承载"最新值"。

## 7. 部署口径

- EMQX 鉴权源配置：`deploy/compose/emqx-configure.sh`（幂等一键脚本，见配置手册）
- backend 依赖：spring-boot-starter-data-redis（RedisTemplate 自动装配）、hivemq-mqtt-client
- 注意：EMQX HTTP 认证回调依赖 backend 存活；backend 不可用时 MQTT 连接会被拒绝（fail-closed，安全优先）。
