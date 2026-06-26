-- PIN codes and game sessions extracted to access-service. Drop legacy
-- tables (and the FK from card_bindings transitively kept account_id only).
drop table if exists game_sessions;
drop table if exists pin_codes;

-- Thin session read-model. Populated by SessionSyncListener consuming
-- session.events; used by JwtAuthenticationFilter to keep auth checks
-- local (no Kafka round-trip per request).
create table if not exists session_snapshot
(
    id           bigint primary key,
    account_id   bigint      not null,
    public_name  varchar(255) not null,
    role         varchar(50) not null,
    session_type varchar(50) not null,
    status       varchar(50) not null,
    started_at   timestamp   not null,
    expires_at   timestamp   not null,
    ended_at     timestamp,
    updated_at   timestamp   not null default now()
);

create index if not exists idx_session_snapshot_account_id on session_snapshot (account_id);
create index if not exists idx_session_snapshot_status on session_snapshot (status);
create index if not exists idx_session_snapshot_expires_at on session_snapshot (expires_at);
