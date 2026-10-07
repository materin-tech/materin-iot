# DFX 参考采集器

Linux 设备健康指标（CPU/内存/磁盘/负载/运行时长）采集与上报。

## 用法

### MQTT 模式（常连设备）
```bash
MODE=mqtt HOST=<emqx地址> PRODUCT_KEY=<产品Key> DEVICE_KEY=<设备Key> \
DEVICE_SECRET=<设备密钥> INTERVAL=60 ./dfx-agent.sh
```

### HTTP 模式（无法长连 MQTT）
```bash
MODE=http URL=http://<平台>:8080/api/v1/device/dfx/report DEVICE_KEY=<设备Key> \
DEVICE_SECRET=<设备密钥> INTERVAL=60 ./dfx-agent.sh
更多接入协议（SNMP/LwM2M）与指标字典见 `docs/dfx-monitoring-design.md`。

### SNMP 路线（Linux 发行版预装 snmpd，零代码）
配置样例见 `docs/dfx/snmpd.conf.sample`，平台侧在设备运维页配置轮询目标即可。
