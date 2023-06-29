--liquibase formatted sql

--changeset kodality:spaces
with t(code, names) as (
    select 'system', '{"en": "System"}'::jsonb
)
insert into sys.space(code, names, active)
select code, names, true from t
 where not exists(select 1 from sys.space where code = t.code)
--
