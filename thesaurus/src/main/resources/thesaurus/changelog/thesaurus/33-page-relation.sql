--liquibase formatted sql

--changeset kodality:page_relation
drop table if exists thesaurus.page_relation;
create table thesaurus.page_relation (
    id                  bigserial not null primary key,
    page_id             bigint not null,
    content_id          bigint not null,
    target              text not null,
    type                text not null,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null,
    constraint page_relation_page_fk foreign key (page_id) references thesaurus.page(id),
    constraint page_relation_content_fk foreign key (page_id) references thesaurus.page_content(id)
);
create index page_relation_page_idx on thesaurus.page_relation(page_id);

select core.create_table_metadata('thesaurus.page_relation');
--rollback drop table if exists thesaurus.page_relation;


--changeset kodality:page_relation_content_fk-fix
alter table thesaurus.page_relation drop constraint page_relation_content_fk;
alter table thesaurus.page_relation add constraint page_relation_content_fk foreign key (content_id) references thesaurus.page_content(id);
--

