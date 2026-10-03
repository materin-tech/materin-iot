USE materin;
CREATE TABLE IF NOT EXISTS open_app (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    app_key     VARCHAR(64)  NOT NULL,
    app_secret  VARCHAR(128) NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_app_key (app_key)
) ENGINE = InnoDB COMMENT '开发者应用';

CREATE TABLE IF NOT EXISTS open_api (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    method      VARCHAR(8)   NOT NULL,
    path        VARCHAR(255) NOT NULL,
    summary     VARCHAR(255) NULL,
    tag         VARCHAR(64)  NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_api (method, path)
) ENGINE = InnoDB COMMENT '平台接口清单';

CREATE TABLE IF NOT EXISTS open_app_api (
    app_id BIGINT NOT NULL,
    api_id BIGINT NOT NULL,
    PRIMARY KEY (app_id, api_id)
) ENGINE = InnoDB COMMENT '应用-接口授权';
