--liquibase formatted sql

--changeset kodality:designation
drop table if exists terminology.designation;
create table terminology.designation (
    id                              bigint default nextval('core.s_entity') primary key,
    designation_type_id             bigint              not null,
    code_system_entity_version_id   bigint              not null,
    name                            text,
    language                        text,
    rendering                       text,
    preferred                       boolean             not null,
    case_significance               text                not null,
    designation_kind                text,
    description                     text,
    status                          text                not null,
    sys_created_at                  timestamp           not null,
    sys_created_by                  text                not null,
    sys_modified_at                 timestamp           not null,
    sys_modified_by                 text                not null,
    sys_status                      char(1) default 'A' not null collate "C",
    sys_version                     int                 not null,
    constraint designation_designation_type_fk foreign key (designation_type_id) references terminology.entity_property(id),
    constraint designation_cs_entity_version_fk foreign key (code_system_entity_version_id) references terminology.code_system_entity_version(id)
);
create index designation_designation_type_idx on terminology.designation(designation_type_id);
create index designation_cs_entity_version_idx on terminology.designation(code_system_entity_version_id);

select core.create_table_metadata('terminology.designation');
--rollback drop table if exists terminology.designation;

--changeset kodality:designation-name-idx
create index designation_name_idx on terminology.designation (lower(name));
--
