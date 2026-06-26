-- Banking module extracted into a standalone banking-service backed by its own database.
-- Main-server no longer owns balance/transaction state; drop the tables it used.

drop table if exists bank_transactions;
drop table if exists idempotency_records;
drop table if exists balances;

-- outbox_events was used by the main-server banking outbox; banking-service has its own.
drop table if exists outbox_events;