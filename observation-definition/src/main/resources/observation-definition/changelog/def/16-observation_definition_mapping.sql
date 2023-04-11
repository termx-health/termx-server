--liquibase formatted sql

--changeset kodality:observation_definition_mapping
drop table if exists def.observation_definition_mapping;
create table def.observation_definition_mapping (
  id                            bigint default nextval('core.s_entity') primary key,
  observation_definition_id     bigint not null,
  target_type                   text not null,
  target_id                     bigint not null,
  order_number                  smallint,
  map_set                       text,
  code_system                   text not null,
  concept                       text not null,
  relation                      text not null,
  condition                     text,
  sys_status                    char(1) default 'A' not null,
  sys_version                   integer not null,
  sys_created_at                timestamptz not null,
  sys_created_by                text not null,
  sys_modified_at               timestamptz,
  sys_modified_by               text,
  constraint observation_definition_mapping_observation_definition_fkey foreign key (observation_definition_id) references def.observation_definition (id)
  );

create index observation_definition_mapping_observation_definition_idx on def.observation_definition_mapping (observation_definition_id);

select core.create_table_metadata('def.observation_definition_mapping');
select audit.add_log('def.observation_definition_mapping');
--


--changeset kodality:observation_definition_mapping-target_id-drop_not_null
alter table def.observation_definition_mapping alter column target_id drop not null;
--
