# 格物 (Materin) · 开源 IoT 平台

物模型驱动的通用 IoT 平台（第一版）。前后端 + 部署编排单仓库交付，
支持私有化部署与多实例水平扩展。

## 平台能力

| 能力 | 说明 |
|---|---|
| RBAC 与认证 | 用户/角色/菜单/部门，JWT 登录态外置 Redis（可吊销、轮换、失败限流），权限码控制 |
| 产品管理 | productKey 自动生成、品类/协议/发布状态、设备接入计数 |
| 物模型 | 属性/方法/事件三要素 JSON 存储，版本与发布状态管理 |
| 设备管理 | 设备密钥一机一密、批量导入、在线状态（滑动 TTL）、最近在线 |
| MQTT 接入 | EMQX 双实例：内部实例（设备 secret 鉴权 + per-device topic ACL）+ 开发者实例（AK/SK 鉴权，`open/` 命名空间），规则 + Sink 消息互通 |
| 指令下发 | requestId + Redis 信箱跨实例回传，同步等待设备应答（超时 504），大报文引用传递 |
| 时序存储 | Apache IoTDB 2.0（standalone） |
| 开发者中心 | 应用（AK/SK）管理、swagger 接口清单同步、接口勾选授权、接入指引 |
| API 文档 | knife4j（`/doc.html`），OpenAPI v3 全量接口/参数/出入参/权限说明 |

## MQTT Topic 规范

统一前缀 `materin/{productKey}/{deviceKey}/`：

| Topic | 方向 | 用途 |
|---|---|---|
| `.../report` | 设备→平台 | 数据上报 |
| `.../event` | 设备→平台 | 事件/告警上报 |
| `.../reply` | 设备→平台 | 指令响应（回带 requestId） |
| `.../cmd` | 平台→设备 | 指令下发 |

完整设计（鉴权链路 / ACL / 共享订阅 / 容量治理）见
[`backend/docs/mqtt-design.md`](backend/docs/mqtt-design.md)。

## 仓库结构

```
materin-iot/
├── backend/                  # 纯 Spring Boot 模块化单体（Java 21，无 Spring Cloud）
│   ├── server/               # 启动入口：装配全部模块，API 统一前缀 /api/v1
│   ├── common/               # 共享内核：R/异常/分页、Redis keys、跨模块 SPI、Topic 常量
│   ├── system/               # 系统板块
│   │   ├── system-rbac/      #   用户/角色/菜单/部门 + JWT 认证
│   │   └── system-openapi/   #   开发者中心：应用(AK/SK)、接口清单、接口授权
│   └── component/            # 功能组件板块
│       ├── device-component/ #   设备/告警/规则/命令下发
│       ├── product-component/#   产品/物模型
│       └── network-component/#   MQTT 接入（EMQX 鉴权回调 + 共享订阅消费）、CoAP（预留）
├── frontend/                 # vben v5 web-antd 精简版（开发者中心等全部页面）
├── deploy/compose/           # 单机部署编排 + 冒烟脚本 + EMQX 集成配置
└── docs/                     # MQTT 设计文档
```

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21 + Spring Boot 3.4 + MyBatis-Flex + MySQL 8.4 + Redis 7.4 |
| MQTT | EMQX 5.8 双实例（内部 / 开发者），HTTP 认证与授权回调 |
| 时序 | Apache IoTDB 2.0（standalone） |
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue（vben v5） |
| 文档 | springdoc OpenAPI v3 + knife4j |
| 部署 | Docker Compose 单机编排 |

## 快速开始

```bash
cd deploy/compose
cp .env.example .env
docker compose up -d          # 7 个服务（首次构建约 10 分钟）
./smoke-test.sh               # 13/13 全绿即部署成功
```

| 入口 | 地址 |
|---|---|
| 平台控制台 | http://localhost:3080 （admin / 123456） |
| 后端 API | http://localhost:8080/api/v1/... |
| knife4j 文档 | http://localhost:8080/doc.html |
| EMQX 管理台（内部） | http://localhost:18083 （admin / public） |
| EMQX 管理台（开发者） | http://localhost:18084 |

本地前后端分离开发：

```bash
# 后端（依赖 compose 内 mysql/redis/emqx）
cd backend && mvn spring-boot:run -pl server

# 前端
cd frontend/vue-vben-admin && pnpm install && pnpm dev:antd   # 5666
```

## 无状态与水平扩展

服务实例不持有任何会话/业务状态：登录态（refresh token/黑名单/限流）、
设备在线态、遥测最新值全部外置 Redis；MQTT 消费采用共享订阅
（`$share/materin-svc/...`），多实例部署由 EMQX 自动负载均衡；
命令应答经 requestId + Redis 信箱跨实例回传。

## License

[MIT](LICENSE)
