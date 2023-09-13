--liquibase formatted sql

--changeset modeler:transformation_definition
create table modeler.transformation_definition (
  id                    bigint default nextval('core.seq_id') not null,
  name                  text not null,
  resources             jsonb not null,
  mapping               jsonb not null,
  test_source           text,
  sys_status                char(1) default 'A' not null,
  sys_version               integer not null,
  sys_created_at            timestamptz not null,
  sys_created_by            text not null,
  sys_modified_at           timestamptz,
  sys_modified_by           text
);

select core.create_table_metadata('modeler.transformation_definition');
--


--changeset modeler:migrate_fhir-source_transformations
with
    resources as (select id, jsonb_array_elements(resources) def from modeler.transformation_definition where sys_status = 'A'),
    modified_resources as (
        select id, def || jsonb_build_object(
                'source', 'url',
                'reference', json_build_object('resourceUrl', (def -> 'reference' ->> 'fhirServer') || '/' || (def -> 'reference' ->> 'fhirResource' ))
        ) modified_def from resources where def ->> 'source' = 'fhir'
        union all
        select * from resources where not def ->> 'source' = 'fhir'
    ),
    grouped as (select id, jsonb_agg(modified_def) resources from modified_resources group by id)
update modeler.transformation_definition td set resources = (select resources from grouped where id  = td.id) where td.id in (select id from grouped);

with
    mappings as (select id, mapping def from modeler.transformation_definition where sys_status = 'A'),
    modified_mappings as (
        select id, def || jsonb_build_object(
            'source', 'url',
            'reference', json_build_object('resourceUrl', (def -> 'reference' ->> 'fhirServer') || '/' || (def -> 'reference' ->> 'fhirResource' ))
        ) modified_def
        from mappings where def ->> 'source' = 'fhir'
    )
update modeler.transformation_definition td set mapping = (select modified_def from modified_mappings where id  = td.id) where td.id in (select id from modified_mappings);
--
