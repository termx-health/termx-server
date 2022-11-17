--liquibase formatted sql

--changeset kodality:structure_definition
drop table if exists thesaurus.structure_definition;
create table thesaurus.structure_definition (
    id                  bigserial not null primary key,
    code                text not null,
    url                 text not null,
    content             text not null,
    content_type        text not null,
    content_format      text not null,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null
);
create unique index structure_definition_code_ukey on thesaurus.structure_definition (code) where (sys_status = 'A');

select core.create_table_metadata('thesaurus.structure_definition');
--rollback drop table if exists thesaurus.structure_definition;

--changeset kodality:structure_definition-version|parent
alter table thesaurus.structure_definition add column version text;
alter table thesaurus.structure_definition add column parent text;
--
