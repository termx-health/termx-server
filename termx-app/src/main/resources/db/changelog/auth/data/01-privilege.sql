--liquibase formatted sql

--changeset kodality:default_privileges
with t (code, names) as (values
                             ('kts-viewer', '{"en": "Provides read access to all resources in the terminology server"}'),
                             ('kts-editor', '{"en": "Gives the permission to view, create and modify all resources, incl import ability."}'),
                             ('kts-publisher', '{"en": "Same as editor with permission to publish resources."}'),
                             ('kts-admin', '{"en": "The user can perform all actions in the application."}')
)
   , e as (select t.*, (exists(select 1 from auth.privilege p where t.code = p.code)) as pexists from t)
   , inserted as (insert into auth.privilege(code, names) select e.code, e.names::jsonb from e where e.pexists = false)
   , updated as (update auth.privilege p set names = e.names::jsonb from e where e.pexists = true and e.code = p.code)
select 1;
--rollback select 1;

--changeset kodality:default_privilege_resources
with t(privilege_code, resource_type, actions) as (values
    ('kts-viewer', 'Any', '{"view": true}'::jsonb),
    ('kts-editor', 'Any', '{"view": true, "edit": true}'::jsonb),
    ('kts-publisher', 'Any', '{"view": true, "edit": true, "publish": true}'::jsonb),
    ('kts-admin', 'Admin', null)
)
   , pr as (select * from auth.privilege p, t where p.code = t.privilege_code)
   , e as (select pr.id as privilege_id, t.*, (exists(select 1 from auth.privilege_resource p where p.privilege_id = pr.id)) as pexists from t, pr where pr.privilege_code = t.privilege_code)
   , inserted as (insert into auth.privilege_resource(privilege_id, resource_type, actions) select e.privilege_id, e.resource_type, e.actions from e where e.pexists = false)
   , updated as (update auth.privilege_resource p set resource_type = e.resource_type, actions = e.actions, resource_id = null from e where e.pexists = true and e.privilege_id = p.privilege_id)
select 1;
--rollback select 1;

--changeset kodality:guest_privileges
with t (code, names) as (values
  ('guest', '{"en": "Provides guest (anonymous) access"}')
)
   , e as (select t.*, (exists(select 1 from auth.privilege p where t.code = p.code)) as pexists from t)
   , inserted as (insert into auth.privilege(code, names) select e.code, e.names::jsonb from e where e.pexists = false)
   , updated as (update auth.privilege p set names = e.names::jsonb from e where e.pexists = true and e.code = p.code)
select 1;
--rollback select 1;

