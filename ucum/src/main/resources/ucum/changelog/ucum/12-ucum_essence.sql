--liquibase formatted sql

--changeset termx:ucum_essence-2
drop table if exists ucum.essence;
create table ucum.essence (
  id                     bigserial primary key,
  version                text not null,
  essence_xml            xml not null,
  sys_status             char(1) default 'A' not null
  );

create unique index ucum_essence_version_uix on ucum.essence(version) where sys_status = 'A';
--rollback drop table if exists ucum.essence;
