-- Local read-model of accounts; populated by Kafka listener on account.events.
create table if not exists account_snapshot
(
    id          bigint primary key,
    public_name varchar(255) not null,
    role        varchar(50)  not null,
    status      varchar(50)  not null,
    updated_at  timestamp    not null default now()
);

create unique index if not exists uq_account_snapshot_public_name
    on account_snapshot (lower(public_name));

-- A physical card binding. One ISSUED card per account at a time; history of
-- BLOCKED / LOST / REPLACED cards persists. The unique cardUid is global.
create table if not exists card_bindings
(
    id                       bigserial primary key,
    account_id               bigint       not null,
    card_uid                 varchar(255) not null unique,
    status                   varchar(50)  not null,
    issued_at                timestamp    not null,
    issued_by_account_id     bigint,
    blocked_at               timestamp,
    blocked_by_account_id    bigint,
    blocked_reason           varchar(255),
    replaced_by_card_id      bigint references card_bindings (id),
    notes                    text,
    created_at               timestamp    not null default now(),
    updated_at               timestamp    not null default now()
);

create index if not exists idx_card_bindings_account_id on card_bindings (account_id);
create index if not exists idx_card_bindings_status on card_bindings (status);

-- Only one ISSUED card per account at a time.
create unique index if not exists uq_card_bindings_active_account
    on card_bindings (account_id)
    where status = 'ISSUED';
