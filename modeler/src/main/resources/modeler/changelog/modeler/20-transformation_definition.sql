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
