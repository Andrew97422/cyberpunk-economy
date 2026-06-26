create table if not exists audit_logs
(
    id                  bigserial primary key,
    event_id            varchar(64),
    event_type          varchar(100) not null,
    event_source        varchar(100),
    aggregate_type      varchar(100),
    aggregate_id        varchar(100),
    actor_account_id    bigint,
    actor_public_name   varchar(255),
    actor_role          varchar(50),
    target_entity_type  varchar(100),
    target_entity_id    varchar(100),
    terminal_id         bigint,
    terminal_name       varchar(100),
    message             text,
    payload_json        text,
    occurred_at         timestamp not null,
    created_at          timestamp not null default now()
);

-- Idempotent ingest: every domain event carries a UUID; a duplicate delivery
-- collides on this index and we ignore it.
create unique index if not exists uq_audit_logs_event_id
    on audit_logs (event_id)
    where event_id is not null;

create index if not exists idx_audit_logs_event_type on audit_logs (event_type);
create index if not exists idx_audit_logs_event_source on audit_logs (event_source);
create index if not exists idx_audit_logs_actor on audit_logs (actor_account_id);
create index if not exists idx_audit_logs_occurred_at on audit_logs (occurred_at desc);
create index if not exists idx_audit_logs_aggregate on audit_logs (aggregate_type, aggregate_id);
