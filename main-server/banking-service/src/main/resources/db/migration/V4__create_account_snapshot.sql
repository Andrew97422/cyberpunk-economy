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

create index if not exists idx_account_snapshot_status
    on account_snapshot (status);