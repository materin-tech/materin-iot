-- V14: 产品节点形态（直连产品 / 网关产品 / 网关子设备产品），决定其下设备默认类型

ALTER TABLE product
    ADD COLUMN node_type VARCHAR(16) NOT NULL DEFAULT 'DIRECT' COMMENT 'DIRECT/GATEWAY/CHILD';

UPDATE product SET node_type = 'DIRECT' WHERE node_type IS NULL OR node_type = '';
