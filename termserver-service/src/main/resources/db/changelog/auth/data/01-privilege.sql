--liquibase formatted sql

--changeset kodality:privileges
with t (code, names) as (values ('admin', '{"en": "Admin"}'),
                             ('kodality.code-system.view', '{"en": "Code system view"}'),
                             ('kodality.code-system.edit', '{"en": "Code system edit"}'),
                             ('kodality.code-system.publish', '{"en": "Code system publish"}'),
                             ('kodality.value-set.view', '{"en": "Value set view"}'),
                             ('kodality.value-set.edit', '{"en": "Value set edit"}'),
                             ('kodality.value-set.publish', '{"en": "Value set publish"}'),
                             ('kodality.map-set.view', '{"en": "Map set view"}'),
                             ('kodality.map-set.edit', '{"en": "Map set edit"}'),
                             ('kodality.map-set.publish', '{"en": "Map set publish"}')
)
   , e as (select t.*, (exists(select 1 from auth.privilege p where t.code = p.code)) as pexists from t)
   , inserted as (insert into auth.privilege(code, names) select e.code, e.names::jsonb from e where e.pexists = false)
   , updated as (update auth.privilege p set names = e.names::jsonb from e where e.pexists = true and e.code = p.code)
select 1;
--rollback select 1;



