# Materin Backend（格物 IoT 平台后端）

纯 Spring Boot（无 Spring Cloud）Maven 多模块单体，包名前缀 `com.materin.tech`。

## 模块结构

```
backend
├── server/                 # 启动入口：装配全部模块，产出可执行 jar
├── common/                 # 共享内核：统一响应 R、异常体系、BaseEntity、NetworkProtocol SPI
├── system/                 # 系统板块（平台自身管理能力）
│   └── system-rbac/        #   用户 / 角色 / 菜单 / 权限
└── component/              # 功能组件板块
    ├── device-component/   #   设备台账 / 生命周期
    └── network-component/  #   网络接入
        ├── network-mqtt/   #     MQTT（对接外部 broker）
        └── network-coap/   #     CoAP（预留）
```

依赖方向：server → system/component → common；模块间不互相依赖，横向协作通过 common 中的 SPI 接口。

## 本地运行

```bash
# 1. 初始化数据库
mysql -uroot -p < sql/init.sql

# 2. 构建
mvn clean package

# 3. 启动（数据库连接可用环境变量覆盖）
java -jar server/target/server-0.1.0-SNAPSHOT.jar
# MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PASSWORD
```

接口（统一前缀 /api/v1，Spring 端通过 ApiVersionWebConfig 自动挂载）：
- `POST /api/v1/auth/login|logout|refresh` + `GET /api/v1/auth/codes` + `GET /api/v1/user/info`（JWT，refresh 走 httpOnly cookie）
- `GET|POST /api/v1/system/user`、`PUT|DELETE /api/v1/system/user/{id}`（角色/菜单/部门同构 /system/role|menu|dept）
- `GET /api/v1/component/devices` 等 device 接口（暂未纳入登录态，待认证接入）
