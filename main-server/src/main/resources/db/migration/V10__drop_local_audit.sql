-- Audit ownership moved to audit-service. Main-server now publishes auth.events
-- to Kafka instead of writing locally; audit-service fan-ins all *.events topics.
drop table if exists audit_logs;
drop table if exists processed_events;
