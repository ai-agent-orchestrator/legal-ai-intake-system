-- 2026-10-23 additive MySQL migration, explicitly limited to legal_intake_lab.
-- Stop writers, verify a full backup/restoration rehearsal and inspect INFORMATION_SCHEMA first.
-- DDL commits implicitly. Do not use --force; review incompatible existing definitions manually.
-- Existing rows keep NULL request keys/hashes and updated_at; legacy version starts at 0.
-- Apply 2026-10-22-intake-permissions-mysql.sql prerequisites only when those columns are absent.
USE legal_intake_lab;
SELECT DATABASE() AS target_schema, VERSION() AS mysql_version;
SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('legal_intake','consultation_memo');

SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='legal_intake' AND COLUMN_NAME='version')=0, 'ALTER TABLE legal_intake ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'SELECT 1 AS column_already_present');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='legal_intake' AND COLUMN_NAME='updated_at')=0, 'ALTER TABLE legal_intake ADD COLUMN updated_at DATETIME(6) NULL', 'SELECT 1 AS column_already_present');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='legal_intake' AND COLUMN_NAME='idempotency_key')=0, 'ALTER TABLE legal_intake ADD COLUMN idempotency_key VARCHAR(120) NULL', 'SELECT 1 AS column_already_present');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='legal_intake' AND COLUMN_NAME='request_hash')=0, 'ALTER TABLE legal_intake ADD COLUMN request_hash VARCHAR(64) NULL', 'SELECT 1 AS column_already_present');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='consultation_memo' AND COLUMN_NAME='request_key')=0, 'ALTER TABLE consultation_memo ADD COLUMN request_key VARCHAR(120) NULL', 'SELECT 1 AS column_already_present');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='consultation_memo' AND COLUMN_NAME='content_hash')=0, 'ALTER TABLE consultation_memo ADD COLUMN content_hash VARCHAR(64) NULL', 'SELECT 1 AS column_already_present');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

-- Existing index of this name must be UNIQUE and have exactly the listed column order.
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='legal_intake' AND INDEX_NAME='uk_legal_intake_owner_idempotency')=0, 'ALTER TABLE legal_intake ADD CONSTRAINT uk_legal_intake_owner_idempotency UNIQUE (owner_username,idempotency_key)', 'SELECT 1 AS index_already_present');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

-- Existing index of this name must be UNIQUE and have exactly the listed column order.
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='consultation_memo' AND INDEX_NAME='uk_consultation_memo_request_key')=0, 'ALTER TABLE consultation_memo ADD CONSTRAINT uk_consultation_memo_request_key UNIQUE (intake_id,request_key)', 'SELECT 1 AS index_already_present');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND INDEX_NAME IN ('uk_legal_intake_owner_idempotency','uk_consultation_memo_request_key') ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;
