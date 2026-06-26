-- Credit: key-rate policy + deposits + loans.
create table if not exists credit_policy (
    id                 bigint primary key,
    key_rate_pct       numeric(19, 2) not null,
    deposit_spread_pct numeric(19, 2) not null,
    loan_spread_pct    numeric(19, 2) not null,
    accrual_minutes    integer        not null,
    max_loan           numeric(19, 2) not null,
    updated_at         timestamp      not null
);

create table if not exists deposits (
    id              bigserial primary key,
    account_id      bigint         not null,
    public_name     varchar(255),
    principal       numeric(19, 2) not null,
    current_amount  numeric(19, 2) not null,
    opened_at       timestamp      not null,
    last_accrued_at timestamp      not null,
    status          varchar(20)    not null,
    closed_at       timestamp
);
create index if not exists idx_dep_account on deposits (account_id);
create index if not exists idx_dep_status on deposits (status);

create table if not exists loans (
    id              bigserial primary key,
    account_id      bigint         not null,
    public_name     varchar(255),
    principal       numeric(19, 2) not null,
    debt            numeric(19, 2) not null,
    opened_at       timestamp      not null,
    last_accrued_at timestamp      not null,
    status          varchar(20)    not null,
    closed_at       timestamp
);
create index if not exists idx_loan_account on loans (account_id);
create index if not exists idx_loan_status on loans (status);
