# 格物 (Materin) · 自建开源 IoT 平台

自建开源、可私有化（air-gap）交付、物模型驱动的通用 IoT 平台。面向 10 万–100 万设备规模，全栈零授权费组件自建，强调离线交付与主权部署。规划与决策记录见 `openspec/changes/init-materin-iot-platform/`（proposal/design/specs），完整论证见 `docs/research-report.md`。

## 平台能力（Phase 1）

| 能力 | 说明 |
|---|---|
| 设备接入 | VerneMQ MQTT 集群（3.1.1 / 5.0）；X.509 mTLS + 一机一密 bcrypt 双通道认证（`secret|mtls|both` 切换）；`materin/{tenant}/{device}/{channel}` per-device topic ACL（default deny）；TLS 强制 |
| 物模型 | 属性/服务/事件三元组，JSONB 存储，minor 加性兼容版本管理，产品模板继承 |
| 设备管理 | 预注册 + JIT 首连激活；未激活/在线/离线/禁用生命周期状态机 + 不可篡改审计 |
| 设备影子 | desired/reported/delta + 单调版本乐观并发；离线下发意图、重连 reconcile |
| 遥测管道 | 设备 → VerneMQ `on_publish` webhook → ingest（按物模型归一化）→ Kafka 原始流（可重放）→ IoTDB；非法消息逐条死信并记录原因 |
| 规则引擎 | Tier1 自研 Java 热路径（消费 Kafka：过滤/即时转发/告警死区去抖/闭环控制，热生效）+ Tier2 Flink SQL（规划中） |
| 多租户 | tenant_id + PostgreSQL RLS + 中间件注入（禁止信任客户端声明）+ 缓存键命名空间 + 配额限流 |
| 开放 API | REST + OAuth2/JWT + scope（`/api/v1/**`），OpenAPI 文档（`/v3/api-docs` + Swagger UI），HMAC 签名 webhook 数据推送（至少一次） |
| 管理控制台 | Vue 3：设备/物模型/影子、遥测实时大屏（SSE）与历史曲线、规则与告警控制台 |
| 独立 Broker 运维系统 | VerneMQ 集群拓扑/会话/监听器/指标/受控配置变更，独立部署独立鉴权，不与平台集成 |

## 数据流

```
上行（异步遥测）：
设备 ──MQTT/TLS──▶ VerneMQ ──on_publish webhook──▶ telemetry-ingest
      （auth/ACL/lifecycle webhook ──▶ gateway ──▶ Kafka materin.device.lifecycle）
telemetry-ingest ──归一化(物模型校验)──▶ Kafka materin.device.raw（可重放）
      ├──▶ open-api Tier1 规则：求值 → 转发(webhook/Kafka)/告警/闭环指令
      └──▶ IoTDB（时序落库，幂等去重）

下行（同步指令）：API/规则 ──▶ 在线：VerneMQ 命令 topic 近实时下发
                        └──▶ 离线：入影子 desired，重连 reconcile 推 delta
```

## 仓库结构

```
iot/
├── backend/                    # Java 17 多模块（Spring Boot 3.4 + WebFlux + Reactor）
│   ├── common/                 # 共享契约：DeviceMessage、ConnectionEvent、MqttTopics topic 方案
│   ├── gateway/                # 接入层：VerneMQ auth/ACL/lifecycle webhook + 生命周期事件→Kafka
│   ├── device-management/      # 物模型 + 设备注册/生命周期 + 影子 + 查询（端口/内存实现）
│   ├── telemetry-ingest/       # on_publish webhook 入口：解析→归一化→Kafka 原始流/死信
│   ├── rule-engine/            # Tier1 逻辑层：求值/转发/告警/闭环（纯库）
│   ├── open-api/               # 开放 API + 控制台 BFF + Tier1 运行时（消费 Kafka）+ 推送
│   ├── platform-core/          # 多租户上下文/RLS 辅助、缓存键、配额限流
│   └── broker-management/      # ★独立服务：VerneMQ 运维管理（独立端口 8090，不依赖其他模块）
├── frontend/                   # 管理控制台（Vue 3 + Vite + Pinia + Element Plus + ECharts）
├── broker-console/             # ★独立前端：Broker 运维管理控制台
├── deploy/                     # 部署制品（详见 deploy/README.md + RUNBOOK.md）
│   ├── vernemq/                # 源码编译 Dockerfile + 自维护 Helm chart（webhook/TLS/集群）
│   ├── kafka/ iotdb/ postgres/ valkey/     # Strimzi/1C3D/CNPG/StatefulSet 清单
│   ├── harbor/ storage/ ingress/ observability/ console/ broker-admin/
│   ├── k8s/                    # K3s（开发）/ RKE2 HA（生产）安装脚本
│   └── compose/                # 本地开发 docker-compose
├── docs/                       # research-report.md（选型论证）、vernemq-admin-api-catalog.md
└── openspec/                   # 规格驱动开发：changes/（进行中变更）+ specs/（能力规格）
```

## 技术栈（已锁定）

| 层 | 选型 |
|---|---|
| MQTT broker | VerneMQ 2.2（源码自编译 Apache-2.0，仅连接与数据传输，不做规则） |
| 消息总线 | Kafka 4.1（KRaft）+ Strimzi，hash(deviceId) 12 分区保序 |
| 时序库 | Apache IoTDB 2.0 |
| 元数据/缓存 | PostgreSQL 18（JSONB + RLS）+ Valkey 9.1 |
| 规则引擎 | 自研 Java Tier1（亚秒热路径）+ Flink SQL Tier2（窗口/异常检测） |
| 后端 | Java 17 + Spring Boot 3.4 + WebFlux + Reactor + R2DBC |
| 前端 | Vue 3 + TypeScript + Vite + Pinia + Element Plus + ECharts |
| 编排/交付 | RKE2（生产）/ K3s（开发）+ Harbor 2.15 + Longhorn 1.11，气隙交付 |

## 本地开发

```bash
# 依赖组件（VerneMQ / Kafka / IoTDB / PostgreSQL / Valkey）
cd deploy/compose && docker compose up -d

# 后端（各服务独立可起；默认无 broker 时用进程内实现，可启动联调）
cd backend && mvn test                          # 全模块测试
cd backend && mvn -pl open-api -am spring-boot:run        # 开放 API + 控制台 BFF（8080）
cd backend && mvn -pl telemetry-ingest -am spring-boot:run # VerneMQ webhook 入口（8080）
cd backend && mvn -pl gateway -am spring-boot:run          # 接入层 webhook（8088）

# 前端
cd frontend && npm install && npm run dev       # 控制台（5173）
cd broker-console && npm install && npm run dev # Broker 运维控制台（5174）

# 生产开关（接真实中间件）
MATERIN_RULES_KAFKA=true        # Tier1 消费 materin.device.raw（open-api）
MATERIN_LIFECYCLE_KAFKA=true    # 生命周期事件落 Kafka（gateway）
SPRING_PROFILES_ACTIVE=json-logs # 结构化 JSON 日志（Loki 采集）
```

## 文档索引

- `deploy/RUNBOOK.md` — 部署/运维 runbook：气隙交付流程、依赖序安装、密钥清单、升级回滚、验收清单
- `deploy/README.md` — 部署制品索引（按组件）
- `docs/research-report.md` — 技术选型与生态论证
- `docs/vernemq-admin-api-catalog.md` — VerneMQ 原生管理/指标接口摸底
- 运行时 API 文档 — open-api 启动后：`/v3/api-docs`（OpenAPI JSON）与 `/swagger-ui.html`
- `openspec/changes/init-materin-iot-platform/` — 本期变更的 proposal / design / specs / tasks（任务级进度与交付证据）

## ⚠️ 交付状态说明

后端/前端代码随测试交付（`mvn test` 全绿口径）；`deploy/` 为部署制品（IaC/清单/Dockerfile/Helm），**未在本环境部署验证**——实际部署、真机联调与端到端冒烟需真实 K8s 集群执行（任务 9.1 批，见 RUNBOOK 验收清单）。当前各服务仓储/通道为进程内实现（端口已抽象），生产 R2DBC/Kafka/Valkey 实现随部署批次替换。
