[English](README.en.md) | [简体中文](README.md)

# Materin (格物) · Open-Source IoT Platform

A thing-model-driven, general-purpose IoT platform (first release). Delivered as a
single repository containing the frontend, backend, and deployment orchestration,
with support for private, self-hosted deployment and horizontally scaled,
multi-instance deployments.

## Platform Capabilities

| Capability | Description |
|---|---|
| RBAC & Authentication | Users / roles / menus / organizations (formerly departments, now generalized), JWT sessions externalized to Redis (revocable, rotating, fail-limited), permission-code access control |
| Security & Compliance (MLPS Level 3) | Password complexity policy, password expiry with reminder and forced rotation, login-failure lockout with configurable threshold/duration, account unlock, admin-initiated password reset, real-name phone numbers and ID numbers (AES-GCM encrypted at rest, masked in list views), forced password change on first login |
| Configuration Management | Security parameters tunable at runtime (sys_config table); changes made in the admin "Security Settings" page take effect immediately |
| Database Migrations | Flyway versioned DDL (backend/server/src/main/resources/db/migration/); existing databases are automatically baselined, new ones created from scratch |
| Product Management | Auto-generated productKey, category / protocol / publish status, connected device count |
| Thing Model | JSON storage of properties / methods / events, with versioning and publish-status management |
| Device Management | Per-device unique secrets (one-device-one-key), bulk import, online status (sliding TTL), last-seen time |
| MQTT Access | Dual EMQX instances: internal (device secret auth + per-device topic ACL) and developer (AK/SK auth, `open/` namespace), bridged via rules + sinks |
| Command Delivery | Cross-instance reply routing via requestId + Redis mailbox, synchronous wait for device response (504 on timeout), large payloads passed by reference |
| Time-Series Storage | Apache IoTDB 2.0 (standalone) |
| Developer Center | Application (AK/SK) management, swagger API inventory sync, per-API authorization, onboarding guides |
| API Documentation | knife4j (`/doc.html`), OpenAPI v3 with full coverage of endpoints, parameters, request/response schemas, and permission notes |

## Secure Defaults

- The `admin` default password `123456` is for first login only; the platform forces a password change on first login (MLPS requirement).
- Production deployments must set the environment variables `MATERIN_SECURITY_DATA_KEY` (the 32-byte AES key used to encrypt ID numbers) and `MATERIN_SECURITY_JWT_SECRET`, otherwise sensitive-data encryption and token signing fall back to insecure defaults.
- Security parameters such as password complexity, expiry, and lockout threshold/duration can be adjusted online in the admin "Security Settings" page; changes take effect immediately.

## MQTT Topic Convention

Unified prefix `materin/{productKey}/{deviceKey}/`:

| Topic | Direction | Purpose |
|---|---|---|
| `.../report` | Device → Platform | Telemetry / data reporting |
| `.../event` | Device → Platform | Event / alarm reporting |
| `.../reply` | Device → Platform | Command response (echoes requestId) |
| `.../cmd` | Platform → Device | Command delivery |

For the full design (auth chain / ACL / shared subscriptions / capacity planning), see
[`backend/docs/mqtt-design.md`](backend/docs/mqtt-design.md).

## Repository Layout

```
materin-iot/
├── backend/                  # Pure Spring Boot modular monolith (Java 21, no Spring Cloud)
│   ├── server/               # Boot entrypoint: wires up all modules, API prefix /api/v1
│   │   └── src/main/resources/db/migration/  # Flyway versioned DDL migrations
│   ├── common/               # Shared kernel: R/errors/pagination, Redis keys, cross-module SPI, topic constants
│   ├── system/               # System segment
│   │   ├── system-rbac/      #   Users / roles / menus / organizations + JWT auth + MLPS security policies
│   │   └── system-openapi/   #   Developer Center: applications (AK/SK), API inventory, API authorization
│   └── component/            # Functional component segment
│       ├── device-component/ #   Devices / alarms / rules / command delivery
│       ├── product-component/#   Products / thing models
│       └── network-component/#   MQTT access (EMQX auth callbacks + shared-subscription consumption), CoAP (reserved)
├── frontend/                 # Trimmed vben v5 web-antd edition (Developer Center and all other pages)
├── deploy/compose/           # Single-host deployment orchestration + smoke tests + EMQX integration config
└── docs/                     # EMQX configuration guide, MQTT design docs (backend/docs/)
```

## Tech Stack

| Layer | Stack |
|---|---|
| Backend | Java 21 + Spring Boot 3.4 + MyBatis-Flex + Flyway + MySQL 8.4 + Redis 7.4 |
| MQTT | EMQX 5.8 dual instances (internal / developer), HTTP auth & authorization callbacks |
| Time-Series | Apache IoTDB 2.0 (standalone) |
| Frontend | Vue 3 + TypeScript + Vite + Ant Design Vue (vben v5) |
| Docs | springdoc OpenAPI v3 + knife4j |
| Deployment | Docker Compose single-host orchestration |

## Quick Start

```bash
cd deploy/compose
cp .env.example .env
docker compose up -d          # 7 services (first build takes ~10 minutes)
./smoke-test.sh               # 13/13 green means deployment succeeded
```

> Note: the `admin` default password `123456` is for first login only; the platform forces a password change on first login (MLPS requirement).
> Production deployments must set the environment variables `MATERIN_SECURITY_DATA_KEY` (the 32-byte AES key for ID numbers)
> and `MATERIN_SECURITY_JWT_SECRET` (the JWT signing secret).

| Entry Point | URL |
|---|---|
| Platform console | http://localhost:3080 (admin / 123456) |
| Backend API | http://localhost:8080/api/v1/... |
| knife4j docs | http://localhost:8080/doc.html |
| EMQX dashboard (internal) | http://localhost:18083 (admin / public) |
| EMQX dashboard (developer) | http://localhost:18084 |

Local frontend/backend development:

```bash
# Backend (depends on mysql/redis/emqx from compose)
cd backend && mvn spring-boot:run -pl server

# Frontend
cd frontend/vue-vben-admin && pnpm install && pnpm dev:antd   # 5666
```

## Stateless & Horizontal Scaling

Service instances hold no session or business state: login state (refresh tokens / blacklist / rate limiting),
device online status, and latest telemetry values all live in Redis; MQTT consumption uses shared subscriptions
(`$share/materin-svc/...`) so EMQX load-balances across instances automatically in multi-instance deployments;
command replies are routed across instances via requestId + Redis mailbox.

## License

[MIT](LICENSE)
