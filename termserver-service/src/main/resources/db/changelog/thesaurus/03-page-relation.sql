--liquibase formatted sql

--changeset kodality:page_relation
drop table if exists thesaurus.page_relation;
create table thesaurus.page_relation (
    id                  bigint default nextval('core.s_entity') primary key,
    source_id           bigint not null,
    target_id           bigint not null,
    order_number        smallint,

    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null,
    constraint page_relation_source_fk foreign key (source_id) references thesaurus.page(id),
    constraint page_relation_target_fk foreign key (target_id) references thesaurus.page(id)
);
create index page_relation_source_idx on thesaurus.page_relation(source_id);
create index page_relation_target_idx on thesaurus.page_relation(target_id);

select core.create_table_metadata('thesaurus.page_relation');
--rollback drop table if exists thesaurus.page_relation;
