-- Crypto exchange: market state (single row) + rate history.
create table if not exists crypto_market_state (
    id         bigint primary key,
    rate       numeric(19, 2)   not null,
    baseline   numeric(19, 2)   not null,
    drift      double precision not null,
    volatility double precision not null,
    min_rate   numeric(19, 2)   not null,
    max_rate   numeric(19, 2)   not null,
    updated_at timestamp        not null
);

create table if not exists crypto_tick (
    id         bigserial primary key,
    rate       numeric(19, 2) not null,
    created_at timestamp      not null
);
create index if not exists idx_crypto_tick_created on crypto_tick (created_at);
