--liquibase formatted sql

--changeset kodality:observation_definition_member
drop table if exists def.observation_definition_member;
create table def.observation_definition_member (
  id                        bigint default nextval('core.s_entity') primary key,
  observation_definition_id bigint not null,
  item_id                   bigint not null,
  order_number              smallint,
  cardinality               jsonb,
  sys_status                char(1) default 'A' not null,
  sys_version               integer not null,
  sys_created_at            timestamptz not null,
  sys_created_by            text not null,
  sys_modified_at           timestamptz,
  sys_modified_by           text,
  constraint observation_definition_member_observation_definition_fkey foreign key (observation_definition_id) references def.observation_definition (id),
  constraint observation_definition_member_item_fkey foreign key (item_id) references def.observation_definition (id)
);

create index observation_definition_member_observation_definition_idx on def.observation_definition_member (observation_definition_id);
create index observation_definition_member_item_idx on def.observation_definition_member (item_id);

select core.create_table_metadata('def.observation_definition_member');
select audit.add_log('def.observation_definition_member');
--
