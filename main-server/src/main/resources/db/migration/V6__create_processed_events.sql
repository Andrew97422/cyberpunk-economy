create table processed_events (
                                  event_id uuid not null,
                                  consumer_name varchar(100) not null,
                                  processed_at timestamp not null default now(),
                                  primary key (event_id, consumer_name)
);

create index idx_processed_events_processed_at
    on processed_events (processed_at);