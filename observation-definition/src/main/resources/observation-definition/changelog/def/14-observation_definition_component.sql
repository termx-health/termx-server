--liquibase formatted sql

--changeset kodality:observation_definition_component
drop table if exists def.observation_definition_component;
create table def.observation_definition_component (
  id                        bigint default nextval('core.s_entity') primary key,
  observation_definition_id bigint not null,
  section_type              text not null,
  code                      text not null,
  names                     jsonb not null,
  order_number              smallint,
  cardinality               jsonb,
  type                      text not null,
  unit                      jsonb,
  value_set                 text,
  sys_status                char(1) default 'A' not null,
  sys_version               integer not null,
  sys_created_at            timestamptz not null,
  sys_created_by            text not null,
  sys_modified_at           timestamptz,
  sys_modified_by           text,
  constraint observation_definition_component_observation_definition_fkey foreign key (observation_definition_id) references def.observation_definition (id)
  );
create index observation_definition_component_observation_definition_idx on def.observation_definition_member (observation_definition_id);
create unique index observation_definition_component_code ON def.observation_definition_component (observation_definition_id, code) WHERE sys_status = 'A';

select core.create_table_metadata('def.observation_definition_component');
select audit.add_log('def.observation_definition_component');
--
