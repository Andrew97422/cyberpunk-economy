create table if not exists terminals
(
    id          bigserial primary key,
    name        varchar(100) not null unique,
    ip_address  varchar(100),
    location    varchar(255),
    terminal_type varchar(50),
    status      varchar(50)  not null default 'ACTIVE',
    notes       text,
    created_at  timestamp    not null default now(),
    updated_at  timestamp    not null default now()
);

create index if not exists idx_terminals_status on terminals (status);
create index if not exists idx_terminals_type on terminals (terminal_type);
