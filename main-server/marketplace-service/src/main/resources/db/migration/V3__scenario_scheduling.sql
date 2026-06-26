-- Time scheduling for economic price scenarios.

-- Schedules that fire a scenario once or repeatedly.
create table if not exists scenario_schedules (
    id bigserial primary key,
    scenario_id bigint not null,
    mode varchar(20) not null,
    start_at timestamp not null,
    interval_seconds bigint,
    end_at timestamp,
    active boolean not null default true,
    next_fire_at timestamp,
    last_fired_at timestamp,
    fire_count integer not null default 0,
    created_at timestamp not null default now()
);
create index if not exists idx_sched_active_next on scenario_schedules (active, next_fire_at);

-- Individual scenario steps queued for timed execution.
create table if not exists pending_steps (
    id bigserial primary key,
    scenario_id bigint not null,
    schedule_id bigint,
    step_json text not null,
    fire_at timestamp not null,
    status varchar(20) not null,
    created_at timestamp not null default now(),
    executed_at timestamp
);
create index if not exists idx_pending_status_fire on pending_steps (status, fire_at);
