-- 全链路追踪：命令日志 / 设备告警 关联 traceId（与报文 traceId 保留键同源）
ALTER TABLE device_command_log
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER request_id;

ALTER TABLE device_alert
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER content;
