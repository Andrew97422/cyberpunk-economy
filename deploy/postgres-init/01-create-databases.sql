-- Runs once on first init of the shared Postgres instance.
-- One server, one database per service (DB-per-service isolation preserved).
-- game_core is created by the POSTGRES_DB env var; create the rest here.
CREATE DATABASE banking_db;
CREATE DATABASE accounts_db;
CREATE DATABASE access_db;
CREATE DATABASE card_db;
CREATE DATABASE terminal_db;
CREATE DATABASE marketplace_db;
CREATE DATABASE news_db;
CREATE DATABASE audit_db;
CREATE DATABASE analytics_db;
