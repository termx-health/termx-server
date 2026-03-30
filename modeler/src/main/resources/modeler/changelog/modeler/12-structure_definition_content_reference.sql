--liquibase formatted sql

--changeset termx:structure_definition_content_reference-cleanup
drop table if exists modeler.structure_definition_content_reference;
--

--changeset termx:structure_definition_content_reference-v2
create table modeler.structure_definition_content_reference (
  id                                bigserial not null,
  structure_definition_version_id   bigint not null,
  url                               text not null,
  resource_type                     text,
  resource_id                       text,
  sys_status                        char(1) default 'A' not null,
  sys_version                       integer not null,
  sys_created_at                    timestamptz not null,
  sys_created_by                    text not null,
  sys_modified_at                   timestamptz,
  sys_modified_by                   text,
  constraint sd_content_ref_pk primary key (id),
  constraint sd_content_ref_version_fk foreign key (structure_definition_version_id)
    references modeler.structure_definition_version(id)
);
create index sd_content_ref_version_idx
  on modeler.structure_definition_content_reference(structure_definition_version_id)
  where (sys_status = 'A');

select core.create_table_metadata('modeler.structure_definition_content_reference');
--rollback drop table if exists modeler.structure_definition_content_reference;
--
