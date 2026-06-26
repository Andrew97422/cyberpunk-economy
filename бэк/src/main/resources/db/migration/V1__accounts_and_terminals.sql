create table if not exists accounts
(
    id              bigserial primary key,
    public_name     varchar(255) not null,
    character_name  varchar(255),
    role            varchar(50)  not null,
    status          varchar(50)  not null,
    password_hash   varchar(255),
    notes           text,
    created_at      timestamp    not null default now(),
    updated_at      timestamp    not null default now()
);

create unique index if not exists uq_accounts_public_name on accounts(public_name);
create index if not exists idx_accounts_role on accounts(role);
create index if not exists idx_accounts_status on accounts(status);

create table if not exists terminals
(
    id          bigserial primary key,
    name        varchar(100) not null unique,
    ip_address  varchar(100),
    location    varchar(255),
    status      varchar(50)  not null default 'ACTIVE',
    notes       text,
    created_at  timestamp    not null default now(),
    updated_at  timestamp    not null default now()
);

create index if not exists idx_terminals_status on terminals(status);