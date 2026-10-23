-- Optional: create the project database manually in an IntelliJ root console.
-- The mysql application profile can also create this database if it is missing.
-- Use the user's root account directly. No GRANT or other account is needed.
CREATE DATABASE IF NOT EXISTS legal_intake_lab
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE legal_intake_lab;
SELECT DATABASE() AS active_schema, CURRENT_USER() AS authenticated_user;
SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'legal_intake_lab';
