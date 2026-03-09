--liquibase formatted sql

--changeset kodality:code_system_entity_version_update_queue
create table if not exists terminology.code_system_entity_version_update_queue (
    id                             bigint default nextval('core.s_entity') primary key,
    code_system_entity_version_id  bigint not null,
    sys_created_at                 timestamp not null,
    sys_created_by                 text not null,
    sys_modified_at                timestamp not null,
    sys_modified_by                text not null,
    sys_status                     char(1) default 'A' not null collate "C",
    sys_version                    int not null,
    constraint code_system_entity_version_update_queue_csev_fk
        foreign key (code_system_entity_version_id) references terminology.code_system_entity_version(id)
);
create unique index if not exists code_system_entity_version_update_queue_csev_ukey
    on terminology.code_system_entity_version_update_queue(code_system_entity_version_id) where (sys_status = 'A');
create index if not exists code_system_entity_version_update_queue_csev_idx
    on terminology.code_system_entity_version_update_queue(code_system_entity_version_id);

select core.create_table_metadata('terminology.code_system_entity_version_update_queue');
--rollback drop table if exists terminology.code_system_entity_version_update_queue;
