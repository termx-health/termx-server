--liquibase formatted sql

--changeset wiki:page_content_migra-1
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 select count(*) from pg_catalog.pg_tables where tablename='page_content' and schemaname = 'thesaurus';
drop index thesaurus.page_content_slug_ukey;
drop index thesaurus.page_content_page_idx;
--

--changeset wiki:page_content
drop table if exists wiki.page_content;

create table wiki.page_content (
    id                  bigint not null generated by default as identity primary key,
    page_id             bigint not null,
    space_id            bigint not null,

    slug                text not null,
    name                text not null,
    lang                text not null,
    content_type        text not null,
    content             text not null,

    sys_status          char(1) default 'A' not null,
    sys_version         integer not null,
    sys_created_at      timestamptz not null,
    sys_created_by      text not null,
    sys_modified_at     timestamptz,
    sys_modified_by     text,

    constraint page_content_page_fk foreign key (page_id) references wiki.page(id),
    constraint page_content_space_fk foreign key (space_id) references sys.space(id)
);
create unique index page_content_slug_ukey on wiki.page_content (slug, space_id) where (sys_status = 'A');
create index page_content_page_idx on wiki.page_content(page_id);

select core.create_table_metadata('wiki.page_content');
--

--changeset wiki:page_content_migra-2
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 select count(*) from pg_catalog.pg_tables where tablename='page_content' and schemaname = 'thesaurus';
insert into wiki.page_content(id, page_id, space_id, slug, name, lang, content_type, content)
select id, page_id, space_id, slug, name, lang, content_type, content from thesaurus.page_content where sys_status = 'A';
select setval('wiki.page_content_id_seq', (select last_value from thesaurus.page_content_id_seq));
--



--changeset wiki:page_content_history
drop table if exists wiki.page_content_history;

create table wiki.page_content_history (
    id                  bigint not null generated by default as identity primary key,
    page_content_id     bigint not null,

    slug                text,
    name                text,
    lang                text,
    content_type        text,
    content             text,

    created_at          timestamptz,
    created_by          text,
    modified_at         timestamptz,
    modified_by         text,

    constraint page_content_page_content_fk foreign key (page_content_id) references wiki.page_content(id)
);
--
