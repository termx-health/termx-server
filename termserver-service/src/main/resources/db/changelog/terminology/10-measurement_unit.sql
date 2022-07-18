--liquibase formatted sql

--changeset kodality:measurement_unit
drop table if exists measurement_unit;
create table measurement_unit (
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
select core.create_table_metadata('measurement_unit');
--rollback drop table measurement_unit;

--changeset kodality:measurement_unit_mapping
drop table if exists measurement_unit_mapping;
create table measurement_unit_mapping (
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
  constraint measurement_unit_mapping_measurement_unit_fk foreign key (measurement_unit_id) references measurement_unit(id)
);
select core.create_table_metadata('measurement_unit_mapping');

create index measurement_unit_mapping_measurement_unit_idx on measurement_unit_mapping (measurement_unit_id);
CREATE UNIQUE INDEX measurement_unit_mapping_uix ON measurement_unit_mapping (measurement_unit_id, system, system_unit) WHERE (sys_status = 'A');
--rollback drop table measurement_unit_mapping;


