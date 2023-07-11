--liquibase formatted sql

--changeset modeler:structure_definition_migra-1
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 select count(*) from pg_catalog.pg_tables where tablename='structure_definition' and schemaname = 'thesaurus';
drop index thesaurus.structure_definition_code_ukey;
--

--changeset modeler:structure_definition-1
create table modeler.structure_definition (
    id                  bigserial not null primary key,
    code                text not null,
    url                 text not null,
    content             text not null,
    content_type        text not null,
    content_format      text not null,
    version             text,
    parent              text,

  sys_status                char(1) default 'A' not null,
  sys_version               integer not null,
  sys_created_at            timestamptz not null,
  sys_created_by            text not null,
  sys_modified_at           timestamptz,
  sys_modified_by           text
);
create unique index structure_definition_code_ukey on modeler.structure_definition (code) where (sys_status = 'A');

select core.create_table_metadata('modeler.structure_definition');
--

--changeset modeler:structure_definition_migra-2
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 select count(*) from pg_catalog.pg_tables where tablename='structure_definition' and schemaname = 'thesaurus';
insert into modeler.structure_definition(id, code, url, content, content_type, content_format, version, parent)
 select id, code, url, content, content_type, content_format, version, parent from thesaurus.structure_definition;
drop table thesaurus.structure_definition;
--
