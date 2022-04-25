--liquibase formatted sql

--changeset kodality:code_system_entity
drop table if exists code_system_entity;
create table code_system_entity (
    id                  bigint      default nextval('core.s_entity') primary key,
    code_system         text                      not null,
    type                text                      not null,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint code_system_entity_code_system_fk foreign key (code_system) references code_system(id)
);
create index code_system_entity_code_system_idx on code_system_entity(code_system);

select core.create_table_metadata('code_system_entity');
--rollback drop table if exists code_system_entity;

--changeset kodality:code_system_entity_version
drop table if exists code_system_entity_version;
create table code_system_entity_version (
    id                      bigint      default nextval('core.s_entity') primary key,
    code_system_entity_id   bigint                    not null,
    code                    text                      not null,
    description             text,
    status                  text                      not null,
    created                 timestamp,
    previous_version_id     bigint,
    sys_created_at          timestamp                 not null,
    sys_created_by          text                      not null,
    sys_modified_at         timestamp                 not null,
    sys_modified_by         text                      not null,
    sys_status              char(1)     default 'A'   not null collate "C",
    sys_version             int                       not null,
    constraint code_system_entity_version_code_system_entity_fk foreign key (code_system_entity_id) references code_system_entity(id),
    constraint code_system_entity_version_previous_version_fk foreign key (previous_version_id) references code_system_entity_version(id)
);
create index code_system_entity_version_code_system_entity_idx on code_system_entity_version(code_system_entity_id);
create index code_system_entity_version_previous_version_idx on code_system_entity_version(previous_version_id);

select core.create_table_metadata('code_system_entity_version');
--rollback drop table if exists code_system_entity_version;

--changeset kodality:entity_version_code_system_version_membership
drop table if exists entity_version_code_system_version_membership;
create table entity_version_code_system_version_membership (
    id                              bigserial                 not null primary key,
    code_system_entity_version_id   bigint                    not null,
    code_system_version_id          bigint                    not null,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint entity_version_cs_version_membership_cs_entity_version_fk foreign key (code_system_entity_version_id) references code_system_entity_version(id),
    constraint entity_version_cs_version_membership_cs_version_fk foreign key (code_system_version_id) references code_system_version(id)
);

select core.create_table_metadata('entity_version_code_system_version_membership');
--rollback drop table if exists entity_version_code_system_version_membership;

--changeset kodality:entity_property
drop table if exists entity_property;
create table entity_property (
    id                  bigint      default nextval('core.s_entity') primary key,
    code_system         text                      not null,
    name                text                      not null,
    description         text,
    status              text                      not null,
    created             timestamptz default now() not null,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint code_system_entity_code_system_fk foreign key (code_system) references code_system(id)
);
create index entity_property_code_system_idx on entity_property(code_system);

select core.create_table_metadata('entity_property');
--rollback drop table if exists entity_property;


--changeset kodality:entity_property_value
drop table if exists entity_property_value;
create table entity_property_value (
    id                              bigint      default nextval('core.s_entity') primary key,
    entity_property_id              bigint                    not null,
    code_system_entity_version_id   bigint                    not null,
    value                           text                      not null,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint entity_property_value_entity_property_fk foreign key (entity_property_id) references entity_property(id),
    constraint entity_property_value_code_system_entity_version_fk foreign key (code_system_entity_version_id) references code_system_entity_version(id)
);
create index entity_property_value_entity_property_idx on entity_property_value(entity_property_id);
create index entity_property_value_code_system_entity_version_idx on entity_property_value(code_system_entity_version_id);

select core.create_table_metadata('entity_property_value');
--rollback drop table if exists entity_property_value;
