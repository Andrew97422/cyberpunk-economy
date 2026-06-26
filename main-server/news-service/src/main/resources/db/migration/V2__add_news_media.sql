alter table news_posts add column if not exists cover_image_url varchar(500);
alter table news_posts add column if not exists gallery_urls jsonb not null default '[]';
