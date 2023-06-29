--liquibase formatted sql

--changeset kodality:page_content
drop table if exists thesaurus.page_content;
create table thesaurus.page_content (
    id                  bigserial not null primary key,
    page_id             bigint not null,
    name                text not null,
    slug                text not null,
    lang                text not null,
    content             text not null,
    content_type        text not null,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null,
    constraint page_content_page_fk foreign key (page_id) references thesaurus.page(id)
);
create unique index page_content_slug_ukey on thesaurus.page_content (slug) where (sys_status = 'A');
create index page_content_page_idx on thesaurus.page_content(page_id);

select core.create_table_metadata('thesaurus.page_content');
--rollback drop table if exists thesaurus.page_content;

--changeset kodality:page_content-space_id
alter table thesaurus.page_content add column space_id bigint constraint page_space_fk references sys.space(id);
update thesaurus.page_content pc set space_id = (select p.space_id from thesaurus.page p where p.id = pc.page_id);
alter table thesaurus.page alter column space_id set not null;

drop index if exists thesaurus.page_content_slug_ukey ;
create unique index page_content_slug_ukey on thesaurus.page_content (slug, space_id) where (sys_status = 'A');
--
