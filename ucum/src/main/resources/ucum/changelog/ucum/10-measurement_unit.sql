--liquibase formatted sql

--changeset kodality:measurement_unit
drop table if exists ucum.measurement_unit;
create table ucum.measurement_unit (
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
select core.create_table_metadata('ucum.measurement_unit');
--rollback drop table ucum.measurement_unit;
