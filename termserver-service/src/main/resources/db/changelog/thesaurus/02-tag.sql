--liquibase formatted sql

--changeset kodality:tag
drop table if exists thesaurus.page_tag;

drop table if exists thesaurus.tag;
create table if not exists thesaurus.tag (
    id                  bigserial not null primary key,
    text                text not null,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null
);

select core.create_table_metadata('thesaurus.tag');
--rollback drop table thesaurus.tag;
