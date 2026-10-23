-- Manual MySQL migration. Check INFORMATION_SCHEMA.COLUMNS before running.
-- Hibernate ddl-auto=update may already have created these two nullable columns.
-- Run only the missing ALTER statement(s); never overwrite owner or author data.
ALTER TABLE legal_intake ADD COLUMN assigned_lawyer_username VARCHAR(100) NULL;
ALTER TABLE consultation_memo ADD COLUMN created_by VARCHAR(100) NULL;
-- Existing NULL owner_username records stay administrator-only.
-- Existing NULL created_by means unknown historical author; do not invent one.
