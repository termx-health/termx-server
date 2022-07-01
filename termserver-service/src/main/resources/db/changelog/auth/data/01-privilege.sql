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

--changeset kodality:privilege-resources
with res(resource_id, resource_type, code) as (values ('publication-status', 'CodeSystem', 'code-system'),
                                                         ('codesystem-content-mode', 'CodeSystem', 'code-system'),
                                                         ('v3-ietf3066', 'CodeSystem', 'code-system'),
                                                         ('concept-property-type', 'CodeSystem', 'code-system'),
                                                         ('contact-point-system', 'CodeSystem', 'code-system'),
                                                         ('contact-point-use', 'CodeSystem', 'code-system'),
                                                         ('filter-operator', 'CodeSystem', 'code-system'),
                                                         ('namingsystem-identifier-type', 'CodeSystem', 'code-system'),
                                                         ('namingsystem-type', 'CodeSystem', 'code-system'),

                                                         ('publication-status', 'ValueSet', 'value-set'),
                                                         ('codesystem-content-mode', 'ValueSet', 'value-set'),
                                                         ('languages', 'ValueSet', 'value-set'),
                                                         ('concept-property-type', 'ValueSet', 'value-set'),
                                                         ('contact-point-system', 'ValueSet', 'value-set'),
                                                         ('contact-point-use', 'ValueSet', 'value-set'),
                                                         ('filter-operator', 'ValueSet', 'value-set'),
                                                         ('namingsystem-identifier-type', 'ValueSet', 'value-set'),
                                                         ('namingsystem-type', 'ValueSet', 'value-set')
)
insert
into auth.privilege_resource (privilege_id, resource_type, resource_id)
select p.id, res.resource_type, res.resource_id
from auth.privilege p, res
where p.code like '%.' || res.code || '.%' and
    not exists(select 1
               from auth.privilege_resource pr
               where pr.resource_id = res.resource_id and
                   pr.resource_type = res.resource_type and
                   pr.privilege_id = p.id);
--rollback select 1;



