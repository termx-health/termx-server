--liquibase formatted sql

--changeset kodality:association_type
drop table if exists association_type;
create table association_type (
    code                text                not null primary key,
    association_kind    text                not null,
    forward_name        text,
    reverse_name        text,
    directed            boolean             not null,
    description         text,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null
);

select core.create_table_metadata('association_type');
--rollback drop table if exists association_type;

--changeset kodality:code_system_association
drop table if exists code_system_association;
create table code_system_association (
    id                                          bigint              not null primary key,
    code_system                                 text                not null,
    source_code_system_entity_version_id        bigint              not null,
    target_code_system_entity_version_id        bigint              not null,
    association_type                            text                not null,
    status                                      text                not null,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null,
    constraint cs_association_id_fk foreign key (id) references code_system_entity(id),
    constraint cs_association_source_cs_entity_version_fk foreign key (source_code_system_entity_version_id) references code_system_entity_version(id),
    constraint cs_association_target_cs_entity_version_fk foreign key (target_code_system_entity_version_id) references code_system_entity_version(id),
    constraint cs_association_code_system_fk foreign key (code_system) references code_system(id),
    constraint cs_association_association_type_fk foreign key (association_type) references association_type(code)
);
create index cs_association_source_cs_entity_version_idx on code_system_association(source_code_system_entity_version_id);
create index cs_association_target_cs_entity_version_idx on code_system_association(target_code_system_entity_version_id);
create index cs_association_association_type_idx on code_system_association(association_type);

select core.create_table_metadata('code_system_association');
--rollback drop table if exists code_system_association;


--changeset kodality:map_set_association
drop table if exists map_set_association;
create table map_set_association (
    id                                          bigint              not null primary key,
    map_set                                     text                not null,
    source_code_system_entity_version_id        bigint              not null,
    target_code_system_entity_version_id        bigint              not null,
    association_type                            text                not null,
    status                                      text                not null,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null,
    constraint ms_association_id_fk foreign key (id) references map_set_entity(id),
    constraint ms_association_map_set_fk foreign key (map_set) references map_set(id),
    constraint ms_association_source_cs_entity_version_fk foreign key (source_code_system_entity_version_id) references code_system_entity_version(id),
    constraint ms_association_target_cs_entity_version_fk foreign key (target_code_system_entity_version_id) references code_system_entity_version(id),
    constraint ms_association_association_type_fk foreign key (association_type) references association_type(code)
);
create index ms_association_map_set_idx on map_set_association(map_set);
create index ms_association_source_cs_entity_version_idx on map_set_association(source_code_system_entity_version_id);
create index ms_association_target_cs_entity_version_idx on map_set_association(target_code_system_entity_version_id);
create index ms_association_association_type_idx on map_set_association(association_type);

select core.create_table_metadata('map_set_association');
--rollback drop table if exists map_set_association;
