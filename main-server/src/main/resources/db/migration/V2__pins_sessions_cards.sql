create table if not exists pin_codes
(
    id                     bigserial primary key,
    account_id             bigint       not null references accounts(id),
    pin_hash               varchar(255) not null,
    duration_minutes       integer      not null,
    status                 varchar(50)  not null,
    used_at                timestamp,
    created_by_account_id  bigint references accounts(id),
    comment                text,
    created_at             timestamp    not null default now(),
    updated_at             timestamp    not null default now()
);

create index if not exists idx_pin_codes_account_id on pin_codes(account_id);
create index if not exists idx_pin_codes_status on pin_codes(status);
create index if not exists idx_pin_codes_created_by_account_id on pin_codes(created_by_account_id);

create table if not exists game_sessions
(
    id                bigserial primary key,
    account_id        bigint      not null references accounts(id),
    pin_code_id       bigint      not null unique references pin_codes(id),
    terminal_id       bigint references terminals(id),
    started_at        timestamp   not null,
    ended_at          timestamp,
    duration_minutes  integer     not null,
    last_seen_at      timestamp,
    status            varchar(50) not null,
    created_at        timestamp   not null default now(),
    updated_at        timestamp   not null default now()
);

create index if not exists idx_game_sessions_account_id on game_sessions(account_id);
create index if not exists idx_game_sessions_status on game_sessions(status);
create index if not exists idx_game_sessions_terminal_id on game_sessions(terminal_id);
create index if not exists idx_game_sessions_started_at on game_sessions(started_at);

create table if not exists card_bindings
(
    id                   bigserial primary key,
    account_id           bigint       not null unique references accounts(id),
    card_uid             varchar(255) not null unique,
    status               varchar(50)  not null,
    issued_at            timestamp    not null,
    issued_by_account_id bigint references accounts(id),
    created_at           timestamp    not null default now(),
    updated_at           timestamp    not null default now()
);

create index if not exists idx_card_bindings_status on card_bindings(status);
create index if not exists idx_card_bindings_issued_by_account_id on card_bindings(issued_by_account_id);