-- V13: 网关设备 + 子设备统一接入（对齐 ThingsBoard Gateway 语义）
-- device_type: DIRECT 直连设备（默认）/ GATEWAY 网关 / CHILD 网关子设备
-- gateway_id:  CHILD 类型设备指向所属网关设备 ID，DIRECT/GATEWAY 为 NULL

ALTER TABLE device ADD COLUMN device_type VARCHAR(16) NOT NULL DEFAULT 'DIRECT' COMMENT 'DIRECT/GATEWAY/CHILD';

ALTER TABLE device ADD COLUMN gateway_id BIGINT NULL COMMENT 'CHILD 指向网关设备 ID';

CREATE INDEX idx_device_gateway ON device (gateway_id);
