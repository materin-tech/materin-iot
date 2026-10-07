-- V11: OTA 升级计划状态机（创建与设备圈选解耦，明细分页添加，防止大产品全量展开 OOM）

-- 任务状态语义变更：0 未启动 1 进行中 2 暂停 3 已终止(终态,不可再启动) 4 已完成
UPDATE ota_task SET status = 4 WHERE status = 1;  -- 旧:已完成 -> 新:已完成
UPDATE ota_task SET status = 3 WHERE status = 2;  -- 旧:已取消 -> 新:已终止
ALTER TABLE ota_task
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
    COMMENT '0-未启动 1-进行中 2-暂停 3-已终止(终态) 4-已完成';

-- 明细状态增加 6-已取消（计划终止时未推送的设备）
ALTER TABLE ota_task_device
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
    COMMENT '0 待推送 1 已推送 2 下载中 3 升级中 4 成功 5 失败 6 已取消',
    ADD KEY idx_task_status (task_id, status);

-- 历史已完成任务旧明细对齐（无动作，状态值 0-5 语义不变）
