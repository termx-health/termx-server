--liquibase formatted sql

--changeset kodality:page
drop materialized view if exists thesaurus.page_link_closure;
drop table if exists thesaurus.page_link;
drop table if exists thesaurus.page_content;
drop table if exists thesaurus.page_relation;
drop table if exists thesaurus.page_tag;
drop table if exists thesaurus.page;
create table thesaurus.page (
    id                  bigserial not null primary key,
    status              text not null,
    template_id         bigint,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null,
    constraint page_template_fk foreign key (template_id) references thesaurus.template(id)
);

select core.create_table_metadata('thesaurus.page');
--rollback drop table if exists thesaurus.page;
