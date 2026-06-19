--liquibase formatted sql

--changeset termx:backfill-snomed-designation-uris-1
-- Designation properties imported BEFORE the designation.use mapping fix (#261) carry the bare SNOMED
-- designation code as their name with no uri, so the use system (http://snomed.info/sct) was lost. Rename
-- each to the shared defined property (snomed-synonym / snomed-fsn / snomed-preferred), set uri = system#code,
-- and link the defined property. Designations reference the property by id (designation_type_id), so the
-- rename does NOT break their links; on read the use Coding is now reconstructed from the property name/uri.
--
-- The user (audit) triggers are disabled around the UPDATE so the sys_columns() audit trigger does not
-- rewrite the sys_modified_* columns / bump sys_version on every backfilled row, then re-enabled. We disable
-- only USER triggers, not ALL: ALL would also target the system RI (foreign-key) triggers, which a non-
-- superuser owner (tx_admin) cannot touch — and which must stay on to preserve referential integrity anyway.
alter table terminology.entity_property disable trigger user;

update terminology.entity_property ep
   set name = m.new_name,
       uri = m.uri,
       defined_entity_property_id = dep.id
  from (values
         ('900000000000013009'::text, 'snomed-synonym'::text,  'http://snomed.info/sct#900000000000013009'::text),
         ('900000000000003001',       'snomed-fsn',            'http://snomed.info/sct#900000000000003001'),
         ('900000000000548007',       'snomed-preferred',      'http://snomed.info/sct#900000000000548007')
       ) as m(code, new_name, uri)
  join terminology.defined_entity_property dep on dep.name = m.new_name and dep.kind = 'designation'
 where ep.kind = 'designation'
   and ep.name = m.code
   -- only rows that have not already been backfilled (still missing the uri or the defined-property link)
   and (ep.uri is null or ep.defined_entity_property_id is null)
   -- skip the (rare) collision where the same code system already declares the target-named property
   and not exists (select 1 from terminology.entity_property e2
                    where e2.code_system = ep.code_system and e2.name = m.new_name and e2.id <> ep.id);

alter table terminology.entity_property enable trigger user;
--rollback select 1;
