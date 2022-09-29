--liquibase formatted sql

--changeset kodality:page
drop table if exists thesaurus.page;
create table thesaurus.page (
    id                  bigint default nextval('core.s_entity') primary key,
    status              text not null,
    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null
);

select core.create_table_metadata('thesaurus.page');
--rollback drop table if exists thesaurus.page;
