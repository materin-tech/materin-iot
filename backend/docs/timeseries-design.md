# 时序存储设计（timeseries-component + IoTDB）

## 1. 模块分层

```
device-component (桥接)          product-component (物模型解析)
    │  TelemetrySink SPI                │  ThingModelLookup SPI
    ▼                                   ▼
timeseries-component (统一抽象, TSDB 无关)
    │  TimeSeriesManager / TimeSeriesService / TimeSeriesData|Metadata|Query
    ▼
iotdb-component (IoTDB 2.0 树模型实现)
```

- 上层业务只依赖 `timeseries-component` 的抽象与 `common/spi` 接口，替换 TSDB 只换实现模块。
- 跨模块引用一律 `ObjectProvider<T>` 注入（可选依赖，缺实现时静默降级）。

## 2. 路径与列设计（设备维度）

```
root.materin.p{productId}.d{deviceId}.{measurement}
```

- 设备维度 = d 节点；属性/事件/方法全部拍平为同设备下的测量列（IoTDB 2.0 测量名不允许点号）。
- 列命名契约：

| 指标类型 | 测量名 | 类型来源 |
|---|---|---|
| 属性 | `{identifier}` | 物模型 dataType → ValueType |
| 事件 | `evt_{eventId}_{param}`（无出参定义时兜底 `evt_{eventId}_data` 整包 JSON） | 同上 |
| 方法 | `cmd_{methodId}_req/_resp`(TEXT)、`_success`(BOOLEAN)、`_cost_ms`(LONG) + `cmd_{id}_in/out_{param}` | 固定四列 + 参数展开 |

- 物模型未建模字段：TEXT 兜底落库（String.valueOf），上行数据不丢。
- 路径安全化：全部段仅 `[0-9a-zA-Z_]`；测量名数字开头补 `m_` 前缀；p/d 节点自带字母前缀无需守卫。

## 3. 写入与建表

- 时序懒建（IoTDB 2.0 无 `CREATE TIMESERIES IF NOT EXISTS`）：内存缓存 + already-exists 消息容错
  （匹配 "already"，覆盖 "already exist" 与 "has already been created as database" 两种实测文案）。
- 编码映射：DOUBLE→GORILLA、LONG→TS_2DIFF、BOOLEAN→RLE、TEXT→PLAIN。
- 建库同样懒建（`CREATE DATABASE root.materin`）。

## 4. 查询

- 树模型 SQL：raw（ORDER BY time / LIMIT ≤ 10000）与聚合（`GROUP BY([start,end), interval)`）。
- 聚合函数白名单：avg/sum/max/min/count/first_value/last_value。
- **窗口上限 10000**：start 缺省 = end − interval×10000（只回看 1 万窗）；
  显式超宽范围直接 400（防窗口物化打爆堆——首版曾因 start=0 默认值 OOM）。
- keyPrefix（evt_/cmd_）经 `SHOW TIMESERIES` 展开为实际列（结果缓存）。
- REST（context-path /api/v1）：
  - `GET /device/{id}/telemetry/history`（keys csv、agg、interval、limit、desc）
  - `GET /device/{id}/events/history`（keyPrefix=evt_）
  - `GET /device/{id}/methods/history`（keyPrefix=cmd_）
- 结果侧按指标类型过滤：属性查询剔除 evt_/cmd_ 列（属性列无前缀，SELECT * 会混入）。

## 5. IoTDB 2.0.11 实测方言约束（踩坑清单）

- tsfile 类迁移：`org.apache.tsfile.*`（依赖 org.apache.tsfile:tsfile:2.4.0），
  `Field.getBoolV()` 而非 getBooleanV()；`SessionDataSet` 在 `org.apache.iotdb.isession`；
  `SessionPool.executeQueryStatement` 返回 `org.apache.iotdb.isession.pool.SessionDataSetWrapper`
  （**组合而非继承**，位于独立 artifact `org.apache.iotdb:isession`，实现 AutoCloseable，next()→RowRecord）。
- 镜像默认 `dn_rpc_address=127.0.0.1`：容器内 healthcheck 全绿（CLI 默认连 loopback）但跨容器不可达。
  compose 用全小写 env `dn_rpc_address: 0.0.0.0` 覆盖（entrypoint 将全小写 env 合入 iotdb-system.properties）；
  healthcheck 同时探测 127.0.0.1 与容器 hostname，确保真实可达性。

## 6. 部署与配置

```
materin.iotdb:
  enabled: true / host: 127.0.0.1 / port: 6667 / username: root / password: root
  database: root.materin / max-pool-size: 8
```

- compose 环境变量：MATERIN_IOTDB_HOST=iotdb、MATERIN_IOTDB_PORT=6667。
- 时序库故障只降级（WARN 日志），不阻断上行链路与指令链路。
