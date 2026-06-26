create table if not exists outbox_events
(
    id             uuid primary key,
    aggregate_type varchar(100) not null,
    aggregate_id   varchar(100) not null,
    event_type     varchar(200) not null,
    payload        jsonb        not null,
    status         varchar(50)  not null default 'NEW',
    created_at     timestamp    not null,
    published_at   timestamp
);

create index if not exists idx_outbox_status_created_at on outbox_events (status, created_at);
create index if not exists idx_outbox_aggregate on outbox_events (aggregate_type, aggregate_id);