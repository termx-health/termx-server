--liquibase formatted sql

--changeset kodality:observation_definition_value
drop table if exists def.observation_definition_value;
create table def.observation_definition_value (
  id                        bigint default nextval('core.s_entity') primary key,
  observation_definition_id bigint not null,
  behaviour                 text not null,
  expression                text,
  type                      text not null,
  unit                      jsonb,
  value_set                 text,
  multiple_results_allowed  boolean not null default false,
  sys_status                char(1) default 'A' not null,
  sys_version               integer not null,
  sys_created_at            timestamptz not null,
  sys_created_by            text not null,
  sys_modified_at           timestamptz,
  sys_modified_by           text,
  constraint observation_definition_value_observation_definition_fkey foreign key (observation_definition_id) references def.observation_definition (id)
);
create index observation_definition_value_observation_definition_idx on def.observation_definition_value (observation_definition_id);
create unique index observation_definition_value_observation_definition ON def.observation_definition_value (observation_definition_id) WHERE sys_status = 'A';

select core.create_table_metadata('def.observation_definition_value');
select audit.add_log('def.observation_definition_value');
--


--changeset kodality:observation_definition_value|usage-values
alter table def.observation_definition_value add column usage text;
alter table def.observation_definition_value add column values jsonb;
--

