# 06 · 关键场景时序

## 1. 设备遥测上报 → 入库（MQTT webhook 链路）

```mermaid
sequenceDiagram
    autonumber
    participant D as 设备
    participant E as EMQX
    participant B as Backend
    participant R as Redis
    participant T as IoTDB
    D->>E: PUBLISH telemetry topic
    E->>B: webhook POST /mqtt/webhook（消息转发）
    B->>B: MqttAccessPolicy 校验主题与设备绑定
    B->>R: 写最新值管道
    B->>T: 时序落库（物模型 schema 校验）
    B-->>E: 200
    Note over B,T: 失败只记日志降级，不阻断 broker（DfxSink 兜底契约）
```

## 2. MQTT 连接鉴权（EMQX webhook）

```mermaid
sequenceDiagram
    autonumber
    participant D as 设备
    participant E as EMQX
    participant B as MqttAuthController
    D->>E: CONNECT clientId/password
    E->>B: POST /mqtt/auth
    B->>B: 查设备凭证 + MqttAccessPolicy
    B-->>E: allow / deny
    E-->>D: CONNACK
```

## 3. OTA 固件上传 → 升级推送

```mermaid
sequenceDiagram
    autonumber
    participant U as 运维
    participant F as 前端
    participant B as ota-component
    participant S as StorageClient
    participant R as FileRegistry
    participant E as EMQX
    participant D as 设备
    U->>F: 上传固件表单
    F->>B: POST /ota/package（multipart）
    B->>S: putObject（流式算 MD5）
    B->>R: register（sys_file 登记）
    B->>B: ota_package 元数据落库
    U->>F: 创建升级计划（产品/设备圈选）
    F->>B: POST /ota/task
    U->>F: 启动计划
    F->>B: POST /task/{id}/start
    B->>E: MQTT 下行固件信息（分批推送）
    E-->>D: 固件消息
    D->>B: 进度/结果上报
```

场景 3 的两个设计要点：
- 升级计划创建只登记口径，不展开设备，设备通过详情分页添加，避免大产品全量展开 OOM（已修复的历史问题）
- 固件删除联动清 sys_file 登记

## 4. 文件管理（本次新增能力）

```mermaid
sequenceDiagram
    autonumber
    participant U as 运维
    participant F as 文件管理页
    participant B as system-file
    participant S as StorageClient
    U->>F: 打开页面
    F->>B: GET /file/list（bizType/keyword 过滤）
    U->>F: 点下载
    F->>B: GET /file/{id}/download-url
    B->>S: presignedGetUrl
    B-->>F: 预签名 URL
    U->>F: 点删除
    F->>B: DELETE /file/{id}
    B->>S: deleteObject
    B->>B: 删 sys_file 记录
```

## 5. DFX 告警链路

```mermaid
sequenceDiagram
    autonumber
    participant DV as 设备
    participant AC as dfx-access-*
    participant P as DfxIngestService（dfx-core）
    participant AR as dfx_alert_rule
    DV->>AC: 指标帧（http/mqtt/snmp 三通道）
    AC->>P: DfxSink.saveDfx
    P->>P: 校验→Redis 最新值→时序库
    P->>P: 匹配 dfx_alert_rule
    P-->>AC: 命中则告警
```

## 6. 登录与权限

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant F as 前端
    participant B as system-rbac
    U->>F: 账号密码
    F->>B: POST /auth/login
    B->>B: BCrypt 校验 + 签发 JWT
    B-->>F: accessToken + httpOnly refresh cookie
    F->>B: GET /auth/codes（权限码）
    F->>B: 菜单接口（sys_menu 树）
    F->>F: 动态路由注册
```
