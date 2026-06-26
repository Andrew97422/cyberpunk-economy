create table outbox_events (
                               id uuid primary key,
                               aggregate_type varchar(100) not null,
                               aggregate_id varchar(100) not null,
                               event_type varchar(200) not null,
                               payload jsonb not null,
                               status varchar(50) not null default 'NEW',
                               created_at timestamp not null default now(),
                               published_at timestamp null
);

create index idx_outbox_events_status_created_at
    on outbox_events (status, created_at);

create index idx_outbox_events_aggregate
    on outbox_events (aggregate_type, aggregate_id);