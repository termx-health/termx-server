--liquibase formatted sql

--changeset kodality:default_privileges
with t (code, names) as (values
    ('viewer', '{"en": "Provides read access to all resources in the terminology server"}'),
    ('editor', '{"en": "Gives the permission to view, create and modify all resources, incl import ability."}'),
    ('publisher', '{"en": "Same as editor with permission to publish resources."}'),
    ('admin', '{"en": "The user can perform all actions in the application."}')
)
   , e as (select t.*, (exists(select 1 from uam.privilege p where t.code = p.code)) as pexists from t)
   , inserted as (insert into uam.privilege(code, names) select e.code, e.names::jsonb from e where e.pexists = false)
   , updated as (update uam.privilege p set names = e.names::jsonb from e where e.pexists = true and e.code = p.code)
select 1;
--rollback select 1;

--changeset kodality:default_privilege_resources
with t(privilege_code, resource_type, actions) as (values
    ('viewer', 'Any', '{"view": true}'::jsonb),
    ('editor', 'Any', '{"view": true, "edit": true}'::jsonb),
    ('publisher', 'Any', '{"view": true, "edit": true, "publish": true}'::jsonb),
    ('admin', 'Admin', null)
)
   , pr as (select * from uam.privilege p, t where p.code = t.privilege_code)
   , e as (select pr.id as privilege_id, t.*, (exists(select 1 from uam.privilege_resource p where p.privilege_id = pr.id)) as pexists from t, pr where pr.privilege_code = t.privilege_code)
   , inserted as (insert into uam.privilege_resource(privilege_id, resource_type, actions) select e.privilege_id, e.resource_type, e.actions from e where e.pexists = false)
   , updated as (update uam.privilege_resource p set resource_type = e.resource_type, actions = e.actions, resource_id = null from e where e.pexists = true and e.privilege_id = p.privilege_id)
select 1;
--rollback select 1;

--changeset kodality:guest_privileges
with t (code, names) as (values
  ('guest', '{"en": "Provides guest (anonymous) access"}')
)
   , e as (select t.*, (exists(select 1 from uam.privilege p where t.code = p.code)) as pexists from t)
   , inserted as (insert into uam.privilege(code, names) select e.code, e.names::jsonb from e where e.pexists = false)
   , updated as (update uam.privilege p set names = e.names::jsonb from e where e.pexists = true and e.code = p.code)
select 1;
--rollback select 1;

--changeset kodality:guest-as-admin
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:'dev' select core.get_setting('core.env');
insert into uam.privilege_resource(privilege_id, resource_type) select id, 'Admin' from uam.privilege p where p.code = 'guest'
--
