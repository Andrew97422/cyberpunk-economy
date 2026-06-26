-- Economic price scenarios: base price tracking + saved scenarios.

-- 1. Capture the original ("base") price so RESET mass-operations can restore it.
alter table products
    add column if not exists base_price numeric(19, 2);

-- Backfill existing rows: treat the current price as the base.
update products
set base_price = price
where base_price is null;

-- 2. Saved, reusable price scenarios. A scenario is an ordered list of price-op steps.
create table if not exists scenarios
(
    id          bigserial primary key,
    name        varchar(150) not null,
    description text,
    steps_json  text         not null default '[]',
    created_at  timestamp    not null default now()
);

create index if not exists idx_scenarios_created_at on scenarios (created_at);
