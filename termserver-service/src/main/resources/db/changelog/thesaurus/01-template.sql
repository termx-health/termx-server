--liquibase formatted sql

--changeset kodality:template
drop materialized view if exists thesaurus.page_link_closure;
drop table if exists thesaurus.page_link;
drop table if exists thesaurus.page_content;
drop table if exists thesaurus.page_relation;
drop table if exists thesaurus.page_tag;
drop table if exists thesaurus.page;

drop table if exists thesaurus.template_content;
drop table if exists thesaurus.template;
create table thesaurus.template (
    id                  bigserial not null primary key,
    code                text not null,
    names               jsonb not null,
    content_type        text not null,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null
);
create unique index template_code_ukey on thesaurus.template (code) where (sys_status = 'A');

select core.create_table_metadata('thesaurus.template');

create table thesaurus.template_content (
    id                  bigserial not null primary key,
    template_id         bigint not null,
    lang                text not null,
    content             text not null,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null,
    constraint template_content_template_fk foreign key (template_id) references thesaurus.template(id)
);
create index template_content_template_idx on thesaurus.template_content(template_id);

select core.create_table_metadata('thesaurus.template_content');
--rollback drop table if exists thesaurus.template;
