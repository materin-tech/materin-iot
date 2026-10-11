# Materin 边缘网关（materin-gateway）

独立边缘网关程序（与 `backend/` 平级的独立 Spring Boot 应用，不依赖 backend 模块），
定位对齐 ThingsBoard IoT Gateway：以一台 **GATEWAY 设备**身份接入 materin-iot 平台，
代理其下子设备完成上下线声明 / 遥测上行 / 属性上报 / 命令下行分发。

内置 4 种配置驱动的 connector：

| type | 说明 |
|------|------|
| `demo` | 虚拟温湿度子设备，周期生成随机遥测（演示/联调用） |
| `mqtt-bridge` | 桥接另一台 MQTT broker，按 topic 段索引提取子设备 |
| `modbus-tcp` | Modbus TCP 主站轮询采集（digitalpetri modbus 2.x），支持寄存器写命令 |
| `opcua` | OPC-UA 节点轮询采集（Eclipse Milo），支持节点写命令 |

## 快速开始

1. **平台创建网关设备**：在 materin-iot 平台创建 GATEWAY 类型产品及其下网关设备，
   记下网关设备的 `deviceKey` 与 `secret`；子设备（如 sensor-01）也需提前建好或由平台自动注册。
2. **填配置**：编辑 `src/main/resources/application.yml`（或用环境变量覆盖）。
3. **构建与启动**：

   ```bash
   mvn package
   MATERIN_GATEWAY_PLATFORM_HOST=127.0.0.1 \
   MATERIN_GATEWAY_PLATFORM_PORT=1883 \
   MATERIN_GATEWAY_PLATFORM_DEVICEKEY=gw-01 \
   MATERIN_GATEWAY_PLATFORM_SECRET=xxx \
   java -jar target/materin-gateway-0.1.0-SNAPSHOT.jar
   ```

   启动后网关自动：连接平台 EMQX → 订阅 `materin/gateway/downlink` → 启动全部 connector。

## 平台协议说明（主题对照表）

所有上行 payload 顶层自动携带 `"gateway": "<网关deviceKey>"` 字段。

| 方向 | 主题 | Payload |
|------|------|---------|
| 上行 | `materin/gateway/connect` | `{"gateway":"gw","device":"dk","name":"子设备名","productKey":"pk-child"}` |
| 上行 | `materin/gateway/disconnect` | `{"gateway":"gw","device":"dk"}` |
| 上行 | `materin/gateway/telemetry` | `{"gateway":"gw","<dk>":[{"ts":毫秒,"values":{"temp":21}}]}` |
| 上行 | `materin/gateway/attributes` | `{"gateway":"gw","<dk>":{"brand":"x"}}` |
| 下行 | `materin/gateway/downlink/{gatewayDeviceKey}`（按网关隔离，ACL 限制只能订自己的） | `{"device":"","requestId":"r1","data":{...},"replyTopic":"materin/{pk}/{dk}/reply"}` |
| 回执 | `replyTopic`（网关代子设备发布） | `{"requestId":"r1","data":{...}}` 或错误信息 |

网关自身遥测：以网关自己的 deviceKey 作为 device 走 `materin/gateway/telemetry`，
周期上报 `uptime` / `connectors` 指标（间隔 `materin.gateway.report-interval-ms`，默认 30s）。

下行命令分发规则：优先分发给"声明了该 deviceKey"的 connector（`devices()`），
无匹配时广播给全部 connector，由 connector 自行忽略不认识的 deviceKey。
下行主题按网关隔离：网关只订阅 `materin/gateway/downlink/{自己的 deviceKey}`。

## productKey 说明

connect 上行可携带可选 `productKey` 字段，声明子设备归属独立产品（产品-物模型-设备逻辑）；
缺省时子设备继承网关所属产品。connector 配置中的 `product-key` 字段会透传到 connect payload。

## 各 connector 命令格式（downlink 的 data 字段）

| connector | data 示例 |
|-----------|-----------|
| demo | `{"set": {"temp": 25}}` — 固定遥测值（先回执再改值） |
| modbus-tcp | `{"writeRegister": {"name": "setpoint", "value": 100}}` / `{"writeCoil": {"name": "pump", "value": true}}` |
| opcua | `{"writeValue": {"name": "temperature", "value": 26.5}}` |
| mqtt-bridge | 原样发布到 mappings 配置的 `command-topic`（`{device}` 占位符替换为 deviceKey） |

## connector 配置字段说明

### demo

| 字段 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `id` | 是 | - | connector 实例标识 |
| `type` | 是 | - | `demo` |
| `interval-ms` | 否 | 10000 | 遥测生成周期（毫秒） |
| `devices[].device-key` | 是 | - | 虚拟子设备 deviceKey |
| `devices[].name` | 否 | deviceKey | 子设备名称 |
| `devices[].product-key` | 否 | - | 子设备归属独立产品的 productKey，缺省继承网关所属产品 |

### mqtt-bridge

| 字段 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `broker-url` | 是 | - | 子 broker 地址，如 `tcp://127.0.0.1:1884` |
| `client-id` | 否 | `materin-bridge-<id>` | MQTT clientId |
| `username` / `password` | 否 | - | 子 broker 凭证 |
| `mappings[].topic` | 是 | - | topic 过滤器，如 `site1/+/data` |
| `mappings[].device-index` | 否 | 0 | topic 段索引（0 起），该段作为子设备 deviceKey |
| `mappings[].command-topic` | 否 | - | 下行命令主题模板，`{device}` 替换为 deviceKey |
| `mappings[].product-key` | 否 | - | 子设备归属独立产品的 productKey，缺省继承网关所属产品 |

### modbus-tcp

| 字段 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `slaves[].host` / `port` | 否 | 127.0.0.1 / 502 | 从站地址 |
| `slaves[].unit-id` | 否 | 1 | 从站单元号 |
| `slaves[].device-key` | 是 | - | 该从站映射的平台子设备 |
| `slaves[].poll-period-ms` | 否 | 1000 | 轮询周期（毫秒） |
| `registers[].name` | 是 | - | 遥测键 / 写命令定位名 |
| `registers[].address` | 是 | - | 寄存器地址 |
| `registers[].function-code` | 否 | readHoldingRegisters | `readHoldingRegisters` / `readInputRegisters` / `readCoils` |
| `registers[].data-type` | 否 | int16 | `int16` / `uint16` / `int32` / `float32`（32 位占 2 寄存器） |
| `registers[].scale` | 否 | 1.0 | 解码缩放系数（原始值 × scale） |

### opcua

| 字段 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `endpoint-url` | 是 | - | 如 `opc.tcp://127.0.0.1:4840` |
| `device-key` | 否 | connector id | 映射的平台子设备 |
| `poll-period-ms` | 否 | 5000 | 轮询周期（毫秒） |
| `nodes[].node-id` | 是 | - | OPC-UA NodeId，如 `ns=2;s=Demo.Static.Double` |
| `nodes[].name` | 否 | node-id | 遥测键 / 写命令定位名 |

> OPC-UA 当前仅支持 `SecurityPolicy.None` + anonymous 身份；Modbus 写命令按 registers 声明的
> `name` 反查地址，int32/float32 写入自动拆为双寄存器。

## connector 开发指南

实现 `com.materin.gateway.core.Connector` 接口并在 `ConnectorRegistry.create()` 注册 type 即可：

```java
public class MyConnector implements Connector {
    public MyConnector(Map<String, Object> cfg) { /* 解析配置（可用 Configs 工具类） */ }
    public String id() { ... }
    public String type() { ... }
    public void start(ConnectorContext ctx) { ... }  // 建立外部连接，经 ctx 上报
    public void stop() { ... }
    public void onCommand(String deviceKey, Map<String, Object> data, String requestId) { ... }
    public Set<String> devices() { ... }  // 声明管理的 deviceKey；空集合 = 动态发现（命令广播）
}
```

- 上行出口 `ConnectorContext`：`connect / disconnect / telemetry / attributes / reply`，
  发布失败只记日志，不会向 connector 抛异常。
- 回执：先 `ctx.reply(...)` 回执成功/失败，再执行副作用（协议约定"先回执再改值"）。
- 失败降级：任何外部连接失败只记日志，不拖垮网关进程。
- 新增 type：在 `ConnectorRegistry.create()` 的 switch 中加一行，未知 type 会得到清晰报错。

## Docker 部署

```bash
mvn package -DskipTests
docker build -t materin-gateway:latest .
docker run -d --name materin-gateway \
  -e MATERIN_GATEWAY_PLATFORM_HOST=192.168.1.10 \
  -e MATERIN_GATEWAY_PLATFORM_DEVICEKEY=gw-01 \
  -e MATERIN_GATEWAY_PLATFORM_SECRET=xxx \
  -e MATERIN_GATEWAY_REPORT_INTERVAL_MS=30000 \
  -v $(pwd)/application-docker.yml:/app/config/application.yml \
  materin-gateway:latest
```

配置优先级：环境变量 > `/app/config/application.yml`（挂载）> 内置默认值。
