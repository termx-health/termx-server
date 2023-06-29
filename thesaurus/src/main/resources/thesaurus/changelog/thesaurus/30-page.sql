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


--changeset kodality:page-space_id
alter table thesaurus.page add column space_id bigint constraint page_space_fk references sys.space(id);
update thesaurus.page set space_id = (select id from sys.space where code = 'system');
alter table thesaurus.page alter column space_id set not null;
--
