USE materin;
ALTER TABLE device ADD COLUMN secret VARCHAR(64) NULL AFTER device_key;
