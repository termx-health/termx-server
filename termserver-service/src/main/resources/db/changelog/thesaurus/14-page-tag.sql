--liquibase formatted sql

--changeset kodality:page_tag
drop table if exists thesaurus.page_tag;
create table thesaurus.page_tag (
    id                  bigserial not null primary key,
    page_id             bigint not null,
    tag_id              bigint not null,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null,
    constraint page_tag_page_fk foreign key (page_id) references thesaurus.page(id),
    constraint page_tag_tag_fk foreign key (tag_id) references thesaurus.tag(id)
);
create index page_tag_page_idx on thesaurus.page_tag(page_id);
create index page_tag_tag_idx on thesaurus.page_tag(tag_id);

select core.create_table_metadata('thesaurus.page_tag');
--rollback drop table if exists thesaurus.page_tag;
