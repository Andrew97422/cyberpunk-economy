create table if not exists balances
(
    id              bigserial primary key,
    account_id      bigint        not null unique,
    cashless_amount numeric(19, 2) not null default 0,
    crypto_amount   numeric(19, 2) not null default 0,
    updated_at      timestamp     not null default now()
);

create index if not exists idx_balances_account_id on balances (account_id);