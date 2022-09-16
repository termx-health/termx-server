--liquibase formatted sql

--changeset kodality:code_system_entity
drop table if exists terminology.code_system_entity;
create table terminology.code_system_entity (
    id                  bigint      default nextval('core.s_entity') primary key,
    code_system         text                      not null,
    type                text                      not null,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint code_system_entity_code_system_fk foreign key (code_system) references terminology.code_system(id)
);
create index code_system_entity_code_system_idx on terminology.code_system_entity(code_system);

select core.create_table_metadata('terminology.code_system_entity');
--rollback drop table if exists terminology.code_system_entity;

--changeset kodality:code_system_entity_version
drop table if exists terminology.code_system_entity_version;
create table terminology.code_system_entity_version (
    id                      bigint      default nextval('core.s_entity') primary key,
    code_system_entity_id   bigint                    not null,
    code_system             text                      not null,
    code                    text                      not null,
    description             text,
    status                  text                      not null,
    created                 timestamp,
    sys_created_at          timestamp                 not null,
    sys_created_by          text                      not null,
    sys_modified_at         timestamp                 not null,
    sys_modified_by         text                      not null,
    sys_status              char(1)     default 'A'   not null collate "C",
    sys_version             int                       not null,
    constraint code_system_entity_version_code_system_entity_fk foreign key (code_system_entity_id) references terminology.code_system_entity(id),
    constraint code_system_entity_version_code_system_fk foreign key (code_system) references terminology.code_system(id)
);
create index code_system_entity_version_code_system_entity_idx on terminology.code_system_entity_version(code_system_entity_id);
create index code_system_entity_version_code_system_idx on terminology.code_system_entity_version(code_system);

select core.create_table_metadata('terminology.code_system_entity_version');
--rollback drop table if exists terminology.code_system_entity_version;

--changeset kodality:entity_version_code_system_version_membership
drop table if exists terminology.entity_version_code_system_version_membership;
create table terminology.entity_version_code_system_version_membership (
    id                              bigserial                 not null primary key,
    code_system_entity_version_id   bigint                    not null,
    code_system_version_id          bigint                    not null,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint entity_version_cs_version_membership_cs_entity_version_fk foreign key (code_system_entity_version_id) references terminology.code_system_entity_version(id),
    constraint entity_version_cs_version_membership_cs_version_fk foreign key (code_system_version_id) references terminology.code_system_version(id)
);
create index entity_version_cs_version_membership_cs_entity_version_idx on terminology.entity_version_code_system_version_membership(code_system_entity_version_id);
create index entity_version_cs_version_membership_cs_version_idx on terminology.entity_version_code_system_version_membership(code_system_version_id);

select core.create_table_metadata('terminology.entity_version_code_system_version_membership');
--rollback drop table if exists terminology.entity_version_code_system_version_membership;

--changeset kodality:entity_property
drop table if exists terminology.entity_property;
create table terminology.entity_property (
    id                  bigint      default nextval('core.s_entity') primary key,
    code_system         text                      not null,
    name                text                      not null,
    type                text                      not null,
    description         text,
    status              text                      not null,
    created             timestamptz default now() not null,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint entity_property_code_system_fk foreign key (code_system) references terminology.code_system(id)
);
create index entity_property_code_system_idx on terminology.entity_property(code_system);

select core.create_table_metadata('terminology.entity_property');
--rollback drop table if exists terminology.entity_property;


--changeset kodality:terminology.entity_property_value
drop table if exists terminology.entity_property_value;
create table terminology.entity_property_value (
    id                              bigint      default nextval('core.s_entity') primary key,
    entity_property_id              bigint                    not null,
    code_system_entity_version_id   bigint                    not null,
    value                           jsonb                     not null,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint entity_property_value_entity_property_fk foreign key (entity_property_id) references terminology.entity_property(id),
    constraint entity_property_value_code_system_entity_version_fk foreign key (code_system_entity_version_id) references terminology.code_system_entity_version(id)
);
create index entity_property_value_entity_property_idx on terminology.entity_property_value(entity_property_id);
create index entity_property_value_code_system_entity_version_idx on terminology.entity_property_value(code_system_entity_version_id);

select core.create_table_metadata('terminology.entity_property_value');
--rollback drop table if exists terminology.entity_property_value;
