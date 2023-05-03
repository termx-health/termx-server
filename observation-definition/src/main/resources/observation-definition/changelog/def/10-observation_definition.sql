--liquibase formatted sql

--changeset kodality:observation_definition-1
drop table if exists def.observation_definition;
create table def.observation_definition (
  id                    bigint default nextval('core.s_entity') primary key,
  code                  text not null,
  version               text default '1' not null,
  publisher             text,
  url                   text not null,
  status                text not null,
  names                 jsonb not null,
  alias                 jsonb,
  definition            jsonb,
  keywords              jsonb,
  category              text not null,
  time_precision        text not null,
  structure             jsonb not null,
  sys_status            char(1) default 'A' not null,
  sys_version           integer not null,
  sys_created_at        timestamptz not null,
  sys_created_by        text not null,
  sys_modified_at       timestamptz,
  sys_modified_by       text
  );

create unique index observation_definition_unique_code_version ON def.observation_definition (code, version) WHERE sys_status = 'A';
create unique index observation_definition_unique_url ON def.observation_definition (url) WHERE sys_status = 'A';


select core.create_table_metadata('def.observation_definition');
select audit.add_log('def.observation_definition');
--

--changeset kodality:observation_definition-category-jsonb
alter table def.observation_definition alter column category type jsonb using jsonb_build_array(jsonb_build_object('code', category, 'codeSystem', 'observation-category'));
--
