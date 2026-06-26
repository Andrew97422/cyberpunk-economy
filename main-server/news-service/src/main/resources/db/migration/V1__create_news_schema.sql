create table if not exists news_posts
(
    id                 bigserial primary key,
    title              varchar(200) not null,
    summary            varchar(500),
    body               text         not null,
    category           varchar(80),
    pinned             boolean      not null default false,
    status             varchar(20)  not null default 'DRAFT',
    publish_at         timestamp,
    published_at       timestamp,
    author_account_id  bigint,
    author_public_name varchar(150),
    created_at         timestamp    not null default now(),
    updated_at         timestamp    not null default now()
);

create index if not exists idx_news_status on news_posts (status);
create index if not exists idx_news_category on news_posts (category);
create index if not exists idx_news_pinned on news_posts (pinned);
create index if not exists idx_news_publish_at on news_posts (publish_at);
