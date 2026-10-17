-- V16: 设备端侧自报信息（OTA 升级上报等链路携带的设备自描述 KV）
-- info_json: 端侧上报的 KV JSON（如 macAddress/serialNumber/chip/hwVersion 等），最新值合并覆盖

ALTER TABLE device ADD COLUMN info_json TEXT NULL COMMENT '端侧设备自报信息 JSON（MAC/SN/芯片等 KV）';
