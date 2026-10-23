-- Connect as root to 127.0.0.1:3306 / legal_intake_lab.
-- Read-only checks. Run before and after starting Spring Boot with profile mysql.
USE legal_intake_lab;

SELECT VERSION() AS server_version, DATABASE() AS active_schema,
       CURRENT_USER() AS authenticated_user, @@port AS server_port;
SHOW GRANTS;

SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;

-- Run these after the first successful Spring Boot startup.
SELECT COUNT(*) AS reference_procedure_count FROM legal_procedure;
SELECT COUNT(*) AS intake_count FROM legal_intake;
SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'intake_document' AND COLUMN_NAME = 'file_content';

-- Tomorrow: replace 1 with the intakeId returned by the API.
-- SELECT id, session_id, owner_username, status FROM legal_intake WHERE id = 1;
-- SELECT id, intake_id, document_type, size_bytes FROM intake_document WHERE intake_id = 1;
-- SELECT id, intake_id FROM lawyer_search_condition WHERE intake_id = 1;
-- SELECT id, intake_id, condition_id, status FROM ai_search_request WHERE intake_id = 1;
