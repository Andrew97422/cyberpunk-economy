alter table game_sessions
    alter column pin_code_id drop not null;

alter table game_sessions
    drop constraint if exists game_sessions_pin_code_id_key;

create unique index if not exists uq_game_sessions_pin_code_id
    on game_sessions(pin_code_id)
    where pin_code_id is not null;