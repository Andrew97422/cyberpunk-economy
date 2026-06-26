-- Account ownership moved to account-service.
-- Main-server keeps the table as a synced read-model; drop the
-- password_hash column (auth lives in account-service) and switch id
-- generation off (we now mirror ids assigned upstream).

alter table accounts drop column if exists password_hash;
alter table accounts alter column id drop default;
drop sequence if exists accounts_id_seq;
