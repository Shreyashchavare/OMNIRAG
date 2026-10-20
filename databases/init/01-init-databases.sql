-- Initialize databases for OMRAGUL microservices
-- This script runs automatically when the PostgreSQL container is first initialized.

SELECT 'CREATE DATABASE identity_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'identity_db')\gexec
SELECT 'CREATE DATABASE knowledge_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'knowledge_db')\gexec
SELECT 'CREATE DATABASE processing_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'processing_db')\gexec
SELECT 'CREATE DATABASE analytics_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'analytics_db')\gexec
SELECT 'CREATE DATABASE notification_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notification_db')\gexec
