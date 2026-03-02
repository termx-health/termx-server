--liquibase formatted sql

--changeset termx:ucum_essence
drop table if exists ucum.essence;
create table ucum.essence (
  id                     bigserial primary key,
  version                text not null,
  essence_xml            xml not null,

  sys_status            char(1) default 'A' not null,
  sys_version           integer not null,
  sys_created_at        timestamp not null,
  sys_created_by        text not null,
  sys_modified_at       timestamp,
  sys_modified_by       text
    );

select core.create_table_metadata('ucum.essence');

create unique index ucum_essence_version_uix on ucum.essence(version) where sys_status = 'A';
--rollback drop table if exists ucum.essence;
