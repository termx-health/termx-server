--liquibase formatted sql

--changeset kodality:page_link
drop materialized view if exists thesaurus.page_link_closure;
drop table if exists thesaurus.page_link;
create table thesaurus.page_link (
    id                  bigserial not null primary key,
    source_id           bigint not null,
    target_id           bigint not null,
    order_number        smallint,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null,
    constraint page_link_source_fk foreign key (source_id) references thesaurus.page(id),
    constraint page_link_target_fk foreign key (target_id) references thesaurus.page(id)
);
create index page_link_source_idx on thesaurus.page_link(source_id);
create index page_link_target_idx on thesaurus.page_link(target_id);

select core.create_table_metadata('thesaurus.page_link');
--rollback drop table if exists thesaurus.page_link;
