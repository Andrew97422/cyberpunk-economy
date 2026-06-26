create table if not exists bank_transactions
(
    id                      bigserial primary key,
    account_id              bigint         not null,
    related_account_id      bigint,
    type                    varchar(50)    not null,
    currency_type           varchar(50)    not null,
    status                  varchar(50)    not null,
    amount                  numeric(19, 2) not null,
    balance_before          numeric(19, 2) not null,
    balance_after           numeric(19, 2) not null,
    comment                 varchar(500),
    created_by_account_id   bigint,
    reversed_transaction_id bigint references bank_transactions (id),
    reversal_applied        boolean        not null default false,
    created_at              timestamp      not null default now()
);

create index if not exists idx_bank_transactions_account_id on bank_transactions (account_id);
create index if not exists idx_bank_transactions_related_account_id on bank_transactions (related_account_id);
create index if not exists idx_bank_transactions_type on bank_transactions (type);
create index if not exists idx_bank_transactions_status on bank_transactions (status);
create index if not exists idx_bank_transactions_created_at on bank_transactions (created_at);
create index if not exists idx_bank_transactions_reversed_transaction_id on bank_transactions (reversed_transaction_id);

create table if not exists idempotency_records
(
    id                    bigserial primary key,
    idempotency_key       varchar(120) not null,
    request_fingerprint   varchar(500) not null,
    created_by_account_id bigint       not null,
    operation             varchar(50)  not null,
    response_payload      text,
    created_at            timestamp    not null default now()
);

create unique index if not exists uq_idempotency_records_key on idempotency_records (idempotency_key);
create index if not exists idx_idempotency_records_operation on idempotency_records (operation);