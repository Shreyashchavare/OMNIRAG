-- Initialize processing_integration_db for OMRAGUL integration testing
-- Executed on virgin volume initialization when mounted in /docker-entrypoint-initdb.d
SELECT 'CREATE DATABASE processing_integration_db OWNER omragul_test_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'processing_integration_db')\gexec

GRANT ALL PRIVILEGES ON DATABASE processing_integration_db TO omragul_test_user;
