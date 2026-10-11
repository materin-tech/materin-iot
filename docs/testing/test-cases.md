# 格物 IoT 平台 — 测试用例设计矩阵

> v1.0（2026-10-11）。执行入口见 [../../tests/README.md](../../tests/README.md)；策略见 [test-design.md](./test-design.md)。
> 优先级：P0=核心闭环（阻断发布） P1=重要 P2=一般；类型：F=功能 B=边界 N=异常 S=安全 P=性能

## TC-01 认证与会话（AuthController · /api/v1/auth, /api/v1/user）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-01-01 | 管理员登录成功 | 登录页输入 admin/Admin@123 → 提交 | 跳转工作台；响应 `code=0`，含 accessToken | P0/F | ✅ Playwright `login.spec.ts` |
| TC-01-02 | 错误密码登录 | 登录页输入 admin/错误密码 → 提交 | toast 错误提示，不跳转 | P0/N | ✅ Playwright `login.spec.ts` |
| TC-01-03 | 未登录访问受保护路由 | 未登录直接访问 `/device` | 重定向登录页 | P0/S | ✅ Playwright（访问后断言 URL 含 login） |
| TC-01-04 | token 过期静默刷新 | 持 refreshToken，accessToken 失效后发请求 | 401 → 自动刷新 → 原请求重放成功 | P0/N | 接口脚本 / 手工 |
| TC-01-05 | 登出后旧 token 失效 | 登出，再用旧 token 调 /device/list | 401 | P0/S | 手工 |
| TC-01-06 | 登录响应携带 X-Trace-Id | 检查登录响应头 | 存在 X-Trace-Id（32 位随机） | P0/F | ✅ Playwright `trace.spec.ts` |
| TC-01-07 | 入站 X-Trace-Id 透传 | `curl -H 'X-Trace-Id: test-trace-001' /device/list` | 响应头回显同值；后端日志含 `[traceId=test-trace-001]` | P0/F | JMeter trace 事务 + 手工 |
| TC-01-08 | 非法 X-Trace-Id 被净化 | 传 `abc<script>` 作为 traceId | 响应头回显净化后 `abcscript`（白名单过滤） | P1/S | 手工 |

## TC-02 用户/角色/菜单/组织/配置（system-rbac）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-02-01 | 用户 CRUD 闭环 | 创建→修改→重置密码→解锁→删除 | 全程 `code=0`，列表随之变化 | P0/F | JMeter 用户事务 |
| TC-02-02 | 角色 CRUD + 菜单授权 | 建角色→绑菜单→绑用户→该用户登录验证权限 | code=0，权限隔离生效 | P1/F | 手工 |
| TC-02-03 | 组织树 CRUD | 建根→建子→移动→删除 | code=0 | P1/F | 手工 |
| TC-02-04 | 配置项读写 | 改配置 → 读回一致 | code=0，值一致 | P2/F | 手工 |
| TC-02-05 | 普通用户越权访问 | 普通用户 token 调管理接口 | 401/403 | P0/S | 手工 |
| TC-02-06 | 弱口令拦截 | reset-password 设置 `123456` | 拒绝并提示策略 | P1/S | 手工 |
| TC-02-07 | 账号锁定/解锁 | 连续错密 N 次 → 锁定 → 解锁 | 锁定后登录拒绝，解锁恢复 | P1/S | 手工 |
| TC-02-08 | 菜单增删与前端渲染联动 | 加菜单→用户端出现入口；删菜单→消失 | 一致 | P2/F | 手工 |

## TC-03 产品与物模型（ProductController / ThingModelController）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-03-01 | 产品 CRUD 闭环 | 创建→查询→修改→删除 | 全程 code=0 | P0/F | JMeter 产品事务 |
| TC-03-02 | 产品导入 | POST /product/import | code=0，列表新增 | P1/F | 手工 |
| TC-03-03 | 物模型定义（属性/服务/事件） | 为产品添加属性+服务+事件并保存 | code=0，详情可见 | P1/F | 手工 |
| TC-03-04 | 有设备引用的产品删除保护 | 先建设备再删产品 | 拒绝（业务错误码） | P1/N | 手工 |
| TC-03-05 | productKey 唯一性 | 重复 productKey 创建 | 拒绝（唯一性守卫） | P1/B | 手工 |

## TC-04 设备管理（DeviceController / DeviceAlertController / DeviceRuleController）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-04-01 | 设备 CRUD 闭环 | 创建→查询→修改→删除 | 全程 code=0 | P0/F | JMeter 设备事务 |
| TC-04-02 | 设备批量导入 | POST /device/import | code=0，数量正确 | P1/F | 手工 |
| TC-04-03 | 离线设备拒绝下发命令 | 对无在线标记设备 POST /device/{id}/cmd | 409「设备离线，拒绝下发」 | P0/N | ✅ Playwright/JMeter（可选） |
| TC-04-04 | 命令下发→应答闭环 | 设备在线（MQTT 上线+订阅 reply）→下发命令 | 设备收到 cmd 报文（含 requestId+traceId）→ 应答后接口返回应答体 | P0/F | 冒烟脚本扩展（TC-MQTT-04 联动） |
| TC-04-05 | 遥测历史查询 | GET /device/{id}/telemetry/history | code=0，分页正确 | P0/F | JMeter 读事务 |
| TC-04-06 | 事件/方法历史查询 | events/history、methods/history | code=0 | P1/F | 手工 |
| TC-04-07 | 告警列表与处理 | 触发 DFX 规则产生告警 → 处理 | 列表可见、状态翻转 | P1/F | 手工 |
| TC-04-08 | 设备规则 CRUD | device-rule 增删改查 | code=0 | P2/F | 手工 |
| TC-04-09 | 大报文应答落库 | 应答 > 16KB | device_command_log 落行，Redis 只存引用 | P1/B | 手工 |

## TC-05 MQTT 接入与网关（MqttAuthController / MqttProtocol / GatewayHandler）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-05-01 | 设备凭证认证回调 | deviceKey/secret 连接 EMQX | 认证通过并可发布 | P0/F | 冒烟扩展 |
| TC-05-02 | 设备越权发布 | 设备向他人 topic 发布 | 拒绝（ACL no-match deny） | P0/S | 冒烟扩展 |
| TC-05-03 | report 上报→Redis 最新值→时序库 | 设备 publish report（含 traceId 保留键） | Redis HASH 更新且**不含** traceId 键；IoTDB 落列；日志 `[traceId=…]` | P0/F | mosquitto_sub/CLI + 日志 |
| TC-05-04 | event 上报 | publish event | IoTDB evt_ 列落库 | P1/F | 手工 |
| TC-05-05 | 网关子设备注册与遥测 | gateway connect/telemetry 帧 | 子设备隐式注册+在线+数据落库 | P0/F | 手工 |
| TC-05-06 | 网关下行命令→子设备→应答回传 | HTTP 下发 → gateway downlink → connector → reply | 命令闭环；device_command_log.trace_id=请求 traceId | P0/F | 手工（全链路核心用例） |
| TC-05-07 | 网关上行报文自动注入 traceId | 订阅 materin/gateway/telemetry 观察帧 | 帧含顶层 `traceId` 字段 | P0/F | mosquitto_sub |
| TC-05-08 | DFX 指标上报 | publish materin/pk/dk/dfx | DfxIngestService 入库；metrics 无 traceId 残留 | P1/F | 手工 |
| TC-05-09 | OTA 进度上报 | publish materin/pk/dk/ota | 进度推进，无 traceId 污染 | P1/F | 手工 |
| TC-05-10 | EMQX 认证回调性能 | 压测 /api/v1/mqtt/auth | P95 < 100ms @ 50 并发 | P1/P | JMeter（可选事务组） |
| TC-05-11 | 网关离线缓冲重发 | 网关断网期间连接器产数据 → 恢复 | PendingMessageBuffer 重发成功 | P2/N | 手工 |

## TC-06 OTA 升级（OtaController）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-06-01 | 固件包上传/列表/删除 | 上传固件→列表可见→删除 | code=0，存储桶对象存在 | P0/F | 手工 |
| TC-06-02 | 固件下载 URL 签发 | GET /ota/package/{id}/download-url | 返回可用 URL | P1/F | 手工 |
| TC-06-03 | 升级任务状态机 | 创建(按产品/按设备)→启动→暂停→恢复→终止 | 状态流转合法，非法流转被拒 | P0/F | 手工 |
| TC-06-04 | 设备圈选 | task/{id}/candidates → task/{id}/devices | 圈选结果正确 | P1/F | 手工 |
| TC-06-05 | 单设备重试 | task/device/{id}/retry | 设备重入队列 | P2/F | 手工 |

## TC-07 DFX 运维监控（DfxHttpAccessController / DfxSnmpTargetController / DfxQueryController / DfxConfigController）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-07-01 | HTTP 接入上报 | POST dfx 指标 | 入库，规则评估执行 | P1/F | 手工 |
| TC-07-02 | SNMP 目标 CRUD | 增删改查 snmp target | code=0 | P1/F | 手工 |
| TC-07-03 | 指标/告警查询 | DfxQueryController 查询 | code=0，分页正确 | P1/F | 手工 |
| TC-07-04 | 告警规则触发 | 制造越限指标 → 规则命中 | device_alert 生成且 trace_id 非空（有链路时） | P1/F | 手工 |

## TC-08 文件与存储（SysFileController / storage 组件）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-08-01 | 文件上传（MinIO） | 上传小文件 | code=0，MinIO 对象存在 | P0/F | 手工 |
| TC-08-02 | 文件下载/外链 | 下载接口/external URL | 内容一致 | P1/F | 手工 |
| TC-08-03 | LOCAL 模式端到端 | MATERIN_STORAGE_TYPE=LOCAL 重启 | 上传下载闭环 | P1/F | 手工 |
| TC-08-04 | 存储唯一性守卫 | 重复上传同文件 | 按唯一性策略处理 | P2/B | 手工 |

## TC-09 OpenAPI 开发者接入（OpenAppController / OpenApiCatalogController / emqx2）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-09-01 | 开发者应用 AK/SK 签发 | 创建 open_app | 返回 appKey/appSecret | P0/F | 手工 |
| TC-09-02 | AK/SK 连接 open broker | 用 AK/SK 连 :11883 | 认证通过，限 open/ 命名空间 | P0/F/S | 手工 |
| TC-09-03 | open 侧越权 | AK/SK 订阅非 open/ 主题 | 拒绝 | P0/S | 手工 |
| TC-09-04 | 目录/凭证查询接口 | OpenApiCatalog 查询 | code=0 | P2/F | 手工 |

## TC-10 全链路追踪（TraceFilter / TraceIds / TraceUtil / 报文保留键）

| 编号 | 标题 | 步骤 | 预期 | 优先级/类型 | 自动化 |
|---|---|---|---|---|---|
| TC-10-01 | HTTP traceId 生成与回显 | 无头请求 → 响应头 X-Trace-Id 为 32 位；带 `X-Trace-Id: abc` 请求 | 响应头回显同值；全程日志含 `[traceId=abc]` | P0/F | ✅ Playwright + JMeter |
| TC-10-02 | MQTT 回调线程 MDC 隔离 | 并发上报多条不同 traceId 报文 | 日志不串号，线程无 MDC 残留 | P1/F | 单测覆盖（TraceIdsTest）+手工 |
| TC-10-03 | 命令链路落库关联 | 下发命令→应答→查 device_command_log | trace_id = HTTP 请求 traceId | P0/F | 冒烟扩展/手工 |
| TC-10-04 | 网关异步回执 traceId 透传 | 网关 downlink 带 traceId → connector 延迟 reply | reply 帧携带同一 traceId | P0/F | 手工 |
| TC-10-05 | 告警关联 traceId | 有链路上下文时触发告警 | device_alert.trace_id 非空 | P1/F | 手工 |
| TC-10-06 | traceId 注入安全 | 超长(>64)/非法字符 traceId | 截断/净化，不落库原值 | P1/S | ✅ TraceIdsTest 覆盖 |