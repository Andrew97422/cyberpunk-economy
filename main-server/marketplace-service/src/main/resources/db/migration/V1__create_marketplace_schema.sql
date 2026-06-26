create table if not exists products
(
    id             bigserial primary key,
    sku            varchar(80)   not null unique,
    name           varchar(150)  not null,
    description    text,
    price          numeric(19, 2) not null,
    currency_type  varchar(20)   not null,
    stock_quantity integer,
    status         varchar(20)   not null default 'ACTIVE',
    category       varchar(80),
    image_url      varchar(500),
    created_at     timestamp     not null default now(),
    updated_at     timestamp     not null default now()
);

create index if not exists idx_products_status on products (status);
create index if not exists idx_products_category on products (category);

create table if not exists market_orders
(
    id                bigserial primary key,
    product_id        bigint        not null,
    product_name      varchar(150)  not null,
    buyer_account_id  bigint        not null,
    buyer_public_name varchar(150),
    quantity          integer       not null,
    unit_price        numeric(19, 2) not null,
    total_price       numeric(19, 2) not null,
    currency_type     varchar(20)   not null,
    status            varchar(30)   not null default 'PENDING_PAYMENT',
    transaction_id    bigint,
    idempotency_key   varchar(100),
    created_at        timestamp     not null default now(),
    updated_at        timestamp     not null default now()
);

create index if not exists idx_market_orders_buyer on market_orders (buyer_account_id);
create index if not exists idx_market_orders_status on market_orders (status);
create index if not exists idx_market_orders_product on market_orders (product_id);
