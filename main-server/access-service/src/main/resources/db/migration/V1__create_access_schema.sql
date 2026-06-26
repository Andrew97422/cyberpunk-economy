-- Local read-model of accounts. Populated by listener on account.events.
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

-- PIN codes used both by players (PIN-only login) and by admins (publicName + PIN).
-- pin_lookup_hash is a deterministic SHA-256 over the raw PIN so we can find the
-- record by index. pin_hash is BCrypt for the final verification.
create table if not exists pin_codes
(
    id                    bigserial primary key,
    account_id            bigint       not null,
    pin_hash              varchar(255) not null,
    pin_lookup_hash       varchar(64)  not null,
    duration_minutes      integer      not null,
    status                varchar(50)  not null,
    used_at               timestamp,
    created_by_account_id bigint,
    comment               text,
    created_at            timestamp    not null default now(),
    updated_at            timestamp    not null default now()
);

create index if not exists idx_pin_codes_account_id on pin_codes (account_id);
create index if not exists idx_pin_codes_status on pin_codes (status);

-- Only one CREATED PIN can match a given raw PIN at a time, which keeps
-- PIN-only login deterministic.
create unique index if not exists uq_pin_codes_active_lookup
    on pin_codes (pin_lookup_hash)
    where status = 'CREATED';

-- Game/admin sessions with an absolute lifetime: expires_at = started_at + duration.
create table if not exists game_sessions
(
    id               bigserial primary key,
    account_id       bigint      not null,
    account_role     varchar(50) not null,
    public_name      varchar(255) not null,
    pin_code_id      bigint references pin_codes (id),
    terminal_id      bigint,
    terminal_name    varchar(100),
    session_type     varchar(50) not null,
    duration_minutes integer     not null,
    started_at       timestamp   not null,
    expires_at       timestamp   not null,
    ended_at         timestamp,
    status           varchar(50) not null,
    created_at       timestamp   not null default now(),
    updated_at       timestamp   not null default now()
);

create index if not exists idx_game_sessions_account_id on game_sessions (account_id);
create index if not exists idx_game_sessions_status on game_sessions (status);
create index if not exists idx_game_sessions_expires_at on game_sessions (expires_at);

create unique index if not exists uq_game_sessions_pin_code_id
    on game_sessions (pin_code_id)
    where pin_code_id is not null;
