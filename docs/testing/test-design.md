# 格物 IoT 平台 — 测试设计

> 版本：v1.0（2026-10-11）　范围：backend / gateway / frontend 全部功能
> 配套文档：[test-cases.md](./test-cases.md)（用例矩阵）、[../../tests/README.md](../../tests/README.md)（自动化执行入口）

## 1. 测试策略总览（分层金字塔）

| 层级 | 载体 | 运行方式 | 覆盖目标 | 现状 |
|---|---|---|---|---|
| L1 单元测试 | JUnit5 + AssertJ（backend/common、各 component、gateway） | `mvn test`（宿主或 Maven 容器） | 工具类、编解码、缓存、解析器、状态机 | ✅ 已有（RTest/TraceIdsTest/ReplyTopicCacheTest 等 100+ 用例） |
| L2 集成冒烟 | compose 全栈 + `smoke-test.sh` | `docker compose up -d && ./smoke-test.sh` | 服务 healthy、核心 API 闭环、EMQX/IoTDB 连通 | ✅ 已有 |
| L3 API 功能 + 压测 | **JMeter 5.6（Docker 化运行）** | `tests/jmeter/run.sh` | REST API 功能回归 + 性能基线 | 🆕 本设计新增 |
| L4 前端 E2E | **Playwright（Docker 化运行）** | `tests/e2e/run.sh` | 关键用户流：登录/设备/产品/链路追踪 | 🆕 本设计新增 |

## 2. 被测系统与环境

### 2.1 架构边界

```
[浏览器] → [frontend nginx :3080 /api 代理] → [backend :8080] → MySQL / Redis / IoTDB / MinIO
                                                   ↕ EMQX(:1883) ← [设备/网关 MQTT 客户端]
                                                   ↕ EMQX-2(:11883) ← [开发者 AK/SK]
```

### 2.2 环境矩阵（全部 Docker 启动）

| 环境 | 用途 | 启动方式 | 被测地址（容器内视角） |
|---|---|---|---|
| `compose 全栈` | 功能回归 / E2E / 压测基线 | `docker compose -f deploy/compose/docker-compose.yml up -d` | backend: `http://backend:8080`（同网络容器）、`http://localhost:8080`（宿主） |
| `压测专项` | 长时间负载 | 同上 + JMeter 容器加入 `materin_default` 网络 | 前端 `http://frontend:80` |

约定：**JMeter 与 Playwright 均以容器方式运行并加入 compose 网络**，测试工具零宿主依赖（用户要求：全部采用 docker 启动）。

### 2.3 测试数据约定

| 数据 | 值 | 来源 |
|---|---|---|
| 管理员账号 | `admin` / `${ADMIN_PASSWORD:-Admin@123}` | deploy/compose/.env |
| API 前缀 | `/api/v1` | ApiVersions |
| 登录响应 | `data.accessToken`（JWT，2h） | AuthController |
| 设备上报主题 | `materin/{pk}/{dk}/report|event|reply|dfx|ota` | MqttTopics |
| 网关主题族 | `materin/gateway/{connect,disconnect,telemetry,attributes,downlink}` | MqttTopics |
| 链路追踪 | 请求头/响应头 `X-Trace-Id`；报文保留键 `traceId`；MDC 键 `traceId` | TraceIds/TraceUtil |

## 3. 各层设计要点

### 3.1 L3 JMeter（后端 API 功能 + 压测）

计划文件：`tests/jmeter/plans/materin-api.jmx`，结构：

```
ThreadGroup(smart-threads: 阶梯 10→50)
 ├── 一次性登录（Once Only Controller）
 │    └── POST /api/v1/auth/login → JSON Extractor accessToken → 设置 Authorization 头
 ├── 业务采样器（HeaderManager 带 Bearer + X-Trace-Id=${__UUID 去杠}）
 │    ├── GET  /api/v1/device/list          （高频读）
 │    ├── POST /api/v1/device               （写：设备创建，deviceKey 随机）
 │    ├── GET  /api/v1/product/list
 │    ├── GET  /api/v1/system/user/list
 │    └── GET  /api/v1/device/1/telemetry/history
 ├── 断言：响应含 "code":0（统一响应包络）
 └── 监听：Summary + Aggregate（-l results.jtl -e -o report/ 生成 HTML 报告）
```

性能基线（单机 compose，作为回归门槛，允许 ±20% 漂移）：

| 指标 | 目标 |
|---|---|
| 登录 P95 | < 500ms |
| 列表读 P95 | < 300ms @ 50 并发 |
| 错误率 | < 0.5% |
| 吞吐 | ≥ 300 req/s（列表读） |

### 3.2 L4 Playwright（前端 E2E）

目录：`tests/e2e/`，Docker 镜像 `mcr.microsoft.com/playwright`。用例选择原则：**关键用户流 + 本次新增能力（X-Trace-Id）**，不做全页面遍历（截图冒烟由人肉/后续视觉回归补充）。

| spec | 覆盖 |
|---|---|
| `login.spec.ts` | 登录成功跳转 / 错误密码提示 / 未登录访问受保护页重定向 |
| `device.spec.ts` | 设备列表加载 / 新建设备成功可见 / 表单校验拦截 |
| `trace.spec.ts` | 任意 API 请求带 `X-Trace-Id` 头；响应头回显同值 |

### 3.3 L1/L2 补充要求

- 新增 MQTT 消费入口必须配套：`TraceIds.setMdc/clearMdc` 包裹的断言单测（见用例 TC-TRACE-*）。
- Testcontainers 本机不可用（Docker Hub 不可达，见记忆），集成层一律走 compose 真实栈，不引入 Testcontainers。

## 4. 通过标准（Definition of Tested）

1. `mvn test` 全绿（backend + gateway）。
2. `smoke-test.sh` PASS=全部 FAIL=0。
3. JMeter 功能集全部断言通过；压测指标满足 §3.1 基线。
4. Playwright 全部 spec 通过（容器内退出码 0）。
5. 全链路追踪抽查：任一 E2E/压测请求的 traceId 能在后端日志 `[traceId=...]` 与 `device_command_log.trace_id` 对上。

## 5. 风险与说明

- compose 单机压测数值仅作**回归基线**，不代表生产容量；生产压测需独立环境。
- `POST /device/{id}/cmd` 依赖在线设备（Redis 在线标记），默认不进压测集；在冒烟脚本中以 MQTT 模拟设备上线后单独验证（TC-MQTT-04）。
- EMQX 鉴权回调把认证压力转嫁给 backend，压测时关注 `/api/v1/mqtt/auth` 的 P95（TC-MQTT-07）。
