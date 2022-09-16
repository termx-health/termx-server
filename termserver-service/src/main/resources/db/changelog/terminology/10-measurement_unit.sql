--liquibase formatted sql

--changeset kodality:measurement_unit
drop table if exists terminology.measurement_unit;
create table terminology.measurement_unit (
  id                    bigint default nextval('core.s_entity') primary key,
  code                  text not null,
  names                 jsonb not null,
  alias                 jsonb not null,
  period                daterange not null,
  
  ordering              smallint,
  rounding              numeric(14,8),
  kind                  text,
  definition_unit       text,
  definition_value      text,
  
  sys_status            char(1) default 'A' not null,
  sys_version           integer not null,
  sys_created_at        timestamp not null,
  sys_created_by        text not null,
  sys_modified_at       timestamp,
  sys_modified_by       text
);
select core.create_table_metadata('terminology.measurement_unit');
--rollback drop table terminology.measurement_unit;

--changeset kodality:measurement_unit_mapping
drop table if exists terminology.measurement_unit_mapping;
create table terminology.measurement_unit_mapping (
  id                    bigserial not null,
  measurement_unit_id   bigint not null,
  system                text not null,
  system_unit           text not null,
  system_value          text not null,
  
  sys_status            char(1) default 'A' not null,
  sys_version           integer not null,
  sys_created_at        timestamp not null,
  sys_created_by        text not null,
  sys_modified_at       timestamp,
  sys_modified_by       text,
  constraint measurement_unit_mapping_measurement_unit_fk foreign key (measurement_unit_id) references terminology.measurement_unit(id)
);
create unique index measurement_unit_mapping_uix on terminology.measurement_unit_mapping (measurement_unit_id, system, system_unit) where (sys_status = 'A');
create index measurement_unit_mapping_measurement_unit_idx on terminology.measurement_unit_mapping (measurement_unit_id);

select core.create_table_metadata('terminology.measurement_unit_mapping');
--rollback drop table terminology.measurement_unit_mapping;


