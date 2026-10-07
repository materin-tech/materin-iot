# deploy/compose —— 格物 IoT 平台单机部署

对应后端：`backend/`（纯 Spring Boot 模块化单体，Java 21 + MyBatis-Flex）。

## 服务清单

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| mysql | mysql:8.4 | 3306 | 业务库，首次启动自动执行 `backend/sql/init.sql` |
| redis | redis:7.4-alpine | 6379 | 缓存（后端暂未消费，预留） |
| emqx | emqx 5.8.6 | 1883 / 8083 / 18083 | EMQX-1 内部实例：设备接入（deviceKey/secret），鉴权回调后端 |
| emqx2 | emqx 5.8.6 | 11883 / 18084 | EMQX-2 开发者实例（核心架构，不可缺失）：第三方应用 AK/SK 接入，仅 open/ 命名空间，与 EMQX-1 通过规则+桥接双向转发 |
| iotdb | apache/iotdb:2.0.11-standalone | 6667 / 18080 | 时序数据库（Apache 官方镜像，REST v2 暂未开启） |
| backend | 本地构建（Dockerfile.backend） | 8080 | Spring Boot 单体，依赖 mysql/emqx healthy |
| frontend | 本地构建（frontend/Dockerfile） | 3080→80 | nginx 托管 vben 打包产物，/api/v1 反代 backend |

镜像走 daocloud / 1ms 镜像源（本机 Docker Hub 不可达），apache/iotdb 由 daemon 直连或代理拉取。

## 使用

```bash
cd deploy/compose
cp .env.example .env      # 按需改密码
docker compose up -d      # emqx-init 服务自动完成双实例鉴权/授权 + 双向桥接，无需手工跑脚本
./smoke-test.sh           # 全绿即部署成功
```

> 双 EMQX 是核心架构设计：EMQX-1 面向设备（deviceKey/secret），EMQX-2 面向第三方开发者
> （AK/SK，open 专用回调，设备凭证拒绝）。broker 无状态可随时重建；EMQX 全部配置由
> `emqx-init` 一次性服务自动写入，手工兜底跑 `./emqx-configure.sh`（幂等）。
> 详细原理见 `docs/emqx-configuration-guide.md`。

## 注意

- IoTDB 首次启动 healthy 需要 1~2 分钟，backend 会等它 healthy 才启动。
- 常用命令：`docker compose logs -f backend` / `docker compose ps`
