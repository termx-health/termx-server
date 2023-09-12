--liquibase formatted sql

--changeset kodality:association_type
drop table if exists terminology.association_type;
create table terminology.association_type (
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
select core.create_table_metadata('terminology.association_type');
--rollback drop table if exists terminology.association_type;

--changeset kodality:code_system_association
drop table if exists terminology.code_system_association;
create table terminology.code_system_association (
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
    constraint cs_association_id_fk foreign key (id) references terminology.code_system_entity(id),
    constraint cs_association_source_cs_entity_version_fk foreign key (source_code_system_entity_version_id) references terminology.code_system_entity_version(id),
    constraint cs_association_target_cs_entity_version_fk foreign key (target_code_system_entity_version_id) references terminology.code_system_entity_version(id),
    constraint cs_association_code_system_fk foreign key (code_system) references terminology.code_system(id),
    constraint cs_association_association_type_fk foreign key (association_type) references terminology.association_type(code)
);
create index cs_association_source_cs_entity_version_idx on terminology.code_system_association(source_code_system_entity_version_id);
create index cs_association_target_cs_entity_version_idx on terminology.code_system_association(target_code_system_entity_version_id);
create index cs_association_code_system_idx on terminology.code_system_association(code_system);
create index cs_association_association_type_idx on terminology.code_system_association(association_type);

select core.create_table_metadata('terminology.code_system_association');
--rollback drop table if exists terminology.code_system_association;

--changeset kodality:code_system_association-order_number
alter table terminology.code_system_association add column order_number smallint;
--

--changeset kodality:map_set_association-1
drop table if exists terminology.map_set_association;
create table terminology.map_set_association (
    id                                          bigint              default nextval('core.s_entity') not null primary key,
    map_set                                     text                not null,
    map_set_version_id                          bigint              not null,
    source_code                                 text                not null,
    source_display                              text,
    source_code_system                          text,
    target_code                                 text,
    target_display                              text,
    target_code_system                          text,
    relationship                                text,
    verified                                    boolean,
    sys_created_at                              timestamp           not null,
    sys_created_by                              text                not null,
    sys_modified_at                             timestamp           not null,
    sys_modified_by                             text                not null,
    sys_status                                  char(1) default 'A' not null collate "C",
    sys_version                                 int                 not null,
    constraint ms_association_map_set_fk foreign key (map_set) references terminology.map_set(id),
    constraint ms_association_map_set_version_fk foreign key (map_set_version_id) references terminology.map_set_version(id),
    constraint ms_association_relationship_fk foreign key (relationship) references terminology.association_type(code)
);
create index ms_association_map_set_idx on terminology.map_set_association(map_set);
create index ms_association_source_cs_id_idx on terminology.map_set_association(source_code_system);
create index ms_association_source_code_idx on terminology.map_set_association(source_code);
create index ms_association_target_cs_id_idx on terminology.map_set_association(target_code_system);
create index ms_association_target_code_idx on terminology.map_set_association(target_code);
create index ms_association_relationship_idx on terminology.map_set_association(relationship);

select core.create_table_metadata('terminology.map_set_association');
--rollback drop table if exists terminology.map_set_association;

--changeset kodality:terminology.map_set_property_value
drop table if exists terminology.map_set_property_value;
create table terminology.map_set_property_value (
    id                              bigint      default nextval('core.s_entity') primary key,
    map_set_property_id             bigint                    not null,
    map_set_association_id          bigint                    not null,
    value                           jsonb                     not null,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint map_set_property_value_map_set_property_fk foreign key (map_set_property_id) references terminology.map_set_property(id),
    constraint map_set_property_value_map_set_association_fk foreign key (map_set_association_id) references terminology.map_set_association(id)
);
create index map_set_property_value_map_set_property_idx on terminology.map_set_property_value(map_set_property_id);
create index map_set_property_value_map_set_association_idx on terminology.map_set_property_value(map_set_association_id);

select core.create_table_metadata('terminology.map_set_property_value');
--rollback drop table if exists terminology.map_set_property_value;

