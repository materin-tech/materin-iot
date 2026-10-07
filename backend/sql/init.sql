-- 格物 IoT 平台：MySQL 初始化脚本（仅建库）
-- 全部 DDL 由后端 Flyway 管理：server/src/main/resources/db/migration/
CREATE DATABASE IF NOT EXISTS materin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
