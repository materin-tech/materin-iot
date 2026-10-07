# 通用 OTA 升级模块 — 设计文档

> 版本：v1.0（2026-10-07） · 已实现并端到端验证（含 MinIO 对象存储 + MQTT 推送/进度闭环）

## 1. 架构

```
ota-component（通用 OTA 业务域，与 broker/存储实现解耦）
├── 固件包管理 OtaPackageService ──┐
├── 升级任务 OtaTaskService        │ 调用
├── 进度状态机（0 待推送 → 1 已推送 → 2 下载中 → 3 升级中 → 4 成功 / 5 失败）
└── OtaMqttSubscriber（$share/materin-ota/materin/+/+/ota 独立共享订阅）
        │ 调用                        │ 发布（CommandPublisher SPI）
        ▼                            ▼
storage-component（存储中间件抽象）   network-mqtt（MQTT 通道）
├── storage-core：StorageClient 统一接口
│     putObject / getObject / deleteObject / statObject / presignedGetUrl
├── storage-minio：MinIO 实现（S3 兼容，@ConditionalOnProperty type=MINIO）
└── 后续：storage-obs（华为）、storage-oss（阿里云）——实现同一接口即插即用
```

设计原则：
- **OTA 只面向 `StorageClient` 接口**，不感知对象存储厂商；上传/下载/预签名全部经中间件。
- 固件文件不落本地磁盘、不进 MySQL——表只存元数据 + `storage_key` + 校验值。
- 推送通道复用平台 MQTT 基础设施（`CommandPublisher` SPI 发布，独立共享订阅消费上行）。
- 新增存储厂商 = 新增 storage-xxx 子模块实现 `StorageClient`，OTA 零改动。

## 2. 设备侧协议（MQTT）

### 2.1 下行：升级推送 `materin/{pk}/{dk}/ota_push`（平台 → 设备）

```json
{
  "taskId": 2, "packageId": 1, "version": "2.0.0", "module": "mcu",
  "size": 48, "signMethod": "md5", "signValue": "3ef1…",
  "url": "http://<minio>/materin/ota/1/…?X-Amz-…"
}
```

- `url` 为**预签名下载地址**（默认 1 小时有效，签名绑定 host——由
  `materin.storage.external-endpoint` 决定，必须是设备可达地址）。
- 设备下载后按 `signMethod/signValue` 校验完整性再升级。

### 2.2 上行：进度上报 `materin/{pk}/{dk}/ota`（设备 → 平台）

```json
{"taskId": 2, "status": 2, "progress": 60, "message": "downloading"}
```

| status | 含义 |
|---|---|
| 1 | 已接收升级指令 |
| 2 | 下载中（progress 0-100） |
| 3 | 升级中 |
| 4 | 成功（平台回写 `device.firmware = version`） |
| 5 | 失败（message 带原因，可在前端重推） |

### 2.3 ACL

- 设备 publish `…/ota`、subscribe `…/ota_push` 由 `MqttTopics.isUpSuffix/isDownPrefix`
  扩展放行（与 report/cmd 同一套设备 namespace 校验）。

## 3. REST 接口（/api/v1/ota）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /ota/package/list | 固件包分页 |
| POST | /ota/package | multipart 上传（文件→存储中间件，流式算 md5/sha256） |
| GET | /ota/package/{id}/download-url | 预签名下载地址 |
| DELETE | /ota/package/{id} | 删除（含对象存储文件） |
| GET | /ota/task/list | 任务分页 |
| POST | /ota/task | 创建任务并立即推送 {packageId, taskName, deviceIds} |
| GET | /ota/task/{id}/devices | 明细分页（状态/进度/消息） |
| POST | /ota/task/device/{id}/retry | 失败重推 |

## 4. 存储中间件配置（materin.storage.*）

| 配置 | 说明 |
|---|---|
| type | MINIO / OBS / ALIYUN / LOCAL / NONE（互斥，多实现共存启动即失败） |
| endpoint | 平台内部读写地址 |
| external-endpoint | 预签名 URL 的 host（S3 签名绑定 host，必须配设备可达地址，如宿主局域网 IP） |
| access-key / secret-key / bucket | 凭证与默认桶（首用自动建桶） |
| local.base-path / local.public-url | LOCAL 模式：落盘根目录 / 设备可达的平台地址（预签名前缀） |

## 5. 前端

- 设备管理 → 固件升级（/ota/packages）：上传/删除固件包
- 设备管理 → 升级任务（/ota/tasks）：建任务（选包+勾选设备）、明细（状态/进度/重推），30s 自动刷新
