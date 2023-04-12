--liquibase formatted sql

--changeset kodality:map_set
drop table if exists terminology.map_set;
create table terminology.map_set (
    id                  text                primary key,
    uri                 text,
    identifiers         jsonb,
    names               jsonb               not null,
    source_value_set    text,
    target_value_set    text,
    contacts            jsonb,
    narrative           text,
    description         text,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null,
    constraint map_set_source_value_set_fk foreign key (source_value_set) references terminology.value_set(id),
    constraint map_set_target_value_set_fk foreign key (target_value_set) references terminology.value_set(id)

);
create unique index map_set_ukey on terminology.map_set (uri) where (sys_status = 'A');
create index map_set_source_value_set_idx on terminology.map_set(source_value_set);
create index map_set_target_value_set_idx on terminology.map_set(target_value_set);

select core.create_table_metadata('terminology.map_set');
--rollback drop table if exists terminology.map_set;

--changeset kodality:map_set_version
drop table if exists terminology.map_set_version;
create table terminology.map_set_version (
    id                  bigint      default nextval('core.s_entity') primary key,
    map_set             text                      not null,
    version             text                      not null,
    source              text,
    supported_languages text[],
    description         text,
    status              text                      not null,
    release_date        timestamp                 not null,
    expiration_date     timestamp,
    created             timestamptz default now() not null,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint map_set_version_map_set_fk foreign key (map_set) references terminology.map_set(id)
);
create index map_set_version_map_set_idx on terminology.map_set_version(map_set);
create unique index map_set_version_ukey on terminology.map_set_version (map_set, version) where (sys_status = 'A');

select core.create_table_metadata('terminology.map_set_version');
--rollback drop table if exists terminology.map_set_version;

--changeset kodality:map_set_entity
drop table if exists terminology.map_set_entity;
create table terminology.map_set_entity (
    id                  bigint      default nextval('core.s_entity') primary key,
    map_set             text                      not null,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint map_set_entity_map_set_fk foreign key (map_set) references terminology.map_set(id)
);
create index map_set_entity_map_set_idx on terminology.map_set_entity(map_set);

select core.create_table_metadata('terminology.map_set_entity');
--rollback drop table if exists terminology.map_set_entity;

--changeset kodality:map_set_entity_version
drop table if exists terminology.map_set_entity_version;
create table terminology.map_set_entity_version (
    id                      bigint      default nextval('core.s_entity') primary key,
    map_set_entity_id       bigint                    not null,
    description             text,
    status                  text                      not null,
    created                 timestamptz default now() not null,
    sys_created_at          timestamp                 not null,
    sys_created_by          text                      not null,
    sys_modified_at         timestamp                 not null,
    sys_modified_by         text                      not null,
    sys_status              char(1)     default 'A'   not null collate "C",
    sys_version             int                       not null,
    constraint map_set_entity_version_map_set_entity_fk foreign key (map_set_entity_id) references terminology.map_set_entity(id)
);
create index map_set_entity_version_map_set_entity_idx on terminology.map_set_entity_version(map_set_entity_id);

select core.create_table_metadata('terminology.map_set_entity_version');
--rollback drop table if exists map_set_entity_version;

--changeset kodality:entity_version_code_system_version_membership
drop table if exists terminology.entity_version_map_set_version_membership;
create table terminology.entity_version_map_set_version_membership (
    id                              bigserial                 not null primary key,
    map_set_entity_version_id       bigint                    not null,
    map_set_version_id              bigint                    not null,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint entity_version_ms_version_membership_ms_entity_version_fk foreign key (map_set_entity_version_id) references terminology.map_set_entity_version(id),
    constraint entity_version_ms_version_membership_ms_version_fk foreign key (map_set_version_id) references terminology.map_set_version(id)
);
create index entity_version_ms_version_membership_ms_entity_version_idx on terminology.entity_version_map_set_version_membership(map_set_entity_version_id);
create index entity_version_ms_version_membership_ms_version_idx on terminology.entity_version_map_set_version_membership(map_set_version_id);

select core.create_table_metadata('terminology.entity_version_map_set_version_membership');
--rollback drop table if exists terminology.entity_version_map_set_version_membership;


--changeset kodality:map_set-source_code_system|target_code_system
alter table terminology.map_set add column source_code_systems jsonb;
alter table terminology.map_set add column target_code_systems jsonb;
--
