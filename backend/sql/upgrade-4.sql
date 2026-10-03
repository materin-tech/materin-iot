USE materin;
CREATE TABLE IF NOT EXISTS device_command_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    request_id  VARCHAR(64)  NOT NULL,
    device_id   BIGINT       NULL,
    direction   TINYINT      NOT NULL DEFAULT 2,
    payload     MEDIUMTEXT   NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cmd_log_rid (request_id)
) ENGINE = InnoDB COMMENT '命令日志（大报文回执落库）';
