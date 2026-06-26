create table if not exists balances
(
    id               bigserial primary key,
    account_id       bigint         not null unique references accounts(id),
    cashless_amount  numeric(19,2)  not null default 0,
    crypto_amount    numeric(19,2)  not null default 0,
    created_at       timestamp      not null default now(),
    updated_at       timestamp      not null default now()
);

create index if not exists idx_balances_account_id on balances(account_id);

create table if not exists bank_transactions
(
    id                      bigserial primary key,
    account_id              bigint         not null references accounts(id),
    related_account_id      bigint references accounts(id),
    type                    varchar(50)    not null,
    currency_type           varchar(50)    not null,
    status                  varchar(50)    not null,
    amount                  numeric(19,2)  not null,
    balance_before          numeric(19,2)  not null,
    balance_after           numeric(19,2)  not null,
    created_by_account_id   bigint references accounts(id),
    comment                 text,
    reversed_transaction_id bigint references bank_transactions(id),
    reversal_applied        boolean        not null default false,
    created_at              timestamp      not null default now(),
    updated_at              timestamp      not null default now()
);

create index if not exists idx_bank_transactions_account_id
    on bank_transactions(account_id);

create index if not exists idx_bank_transactions_related_account_id
    on bank_transactions(related_account_id);

create index if not exists idx_bank_transactions_type
    on bank_transactions(type);

create index if not exists idx_bank_transactions_currency_type
    on bank_transactions(currency_type);

create index if not exists idx_bank_transactions_status
    on bank_transactions(status);

create index if not exists idx_bank_transactions_created_by_account_id
    on bank_transactions(created_by_account_id);

create index if not exists idx_bank_transactions_created_at
    on bank_transactions(created_at);

create index if not exists idx_bank_transactions_reversed_transaction_id
    on bank_transactions(reversed_transaction_id);

create table if not exists idempotency_records
(
    id                    bigserial primary key,
    idempotency_key       varchar(120) not null,
    request_fingerprint   varchar(500) not null,
    created_by_account_id bigint       not null references accounts(id),
    operation             varchar(50)  not null,
    response_payload      text,
    created_at            timestamp    not null default now()
);

create unique index if not exists uq_idempotency_records_key
    on idempotency_records(idempotency_key);

create index if not exists idx_idempotency_records_created_by_account_id
    on idempotency_records(created_by_account_id);

create index if not exists idx_idempotency_records_operation
    on idempotency_records(operation);

create table if not exists audit_logs
(
    id                 bigserial primary key,
    event_type         varchar(100) not null,
    actor_account_id   bigint references accounts(id),
    target_entity_type varchar(100),
    target_entity_id   bigint,
    terminal_id        bigint references terminals(id),
    message            text,
    metadata_json      text,
    created_at         timestamp not null default now(),
    updated_at         timestamp not null default now()
);

create index if not exists idx_audit_logs_event_type on audit_logs(event_type);
create index if not exists idx_audit_logs_actor_account_id on audit_logs(actor_account_id);
create index if not exists idx_audit_logs_target_entity on audit_logs(target_entity_type, target_entity_id);
create index if not exists idx_audit_logs_terminal_id on audit_logs(terminal_id);
create index if not exists idx_audit_logs_created_at on audit_logs(created_at);