--liquibase formatted sql

--changeset wiki:tag
drop table if exists wiki.page_tag;
drop table if exists wiki.tag;

create table if not exists wiki.tag (
    id                  bigint not null generated by default as identity primary key,
    text                text not null,

    sys_status          char(1) default 'A' not null,
    sys_version         integer not null,
    sys_created_at      timestamptz not null,
    sys_created_by      text not null,
    sys_modified_at     timestamptz,
    sys_modified_by     text
);

select core.create_table_metadata('wiki.tag');
--

--changeset wiki:tag_migra-1
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 select count(*) from pg_catalog.pg_tables where tablename='tag' and schemaname = 'thesaurus';
insert into wiki.tag(id, text)
select id, text from thesaurus.tag where sys_status = 'A';
select setval('wiki.tag_id_seq', (select last_value from thesaurus.tag_id_seq));
--