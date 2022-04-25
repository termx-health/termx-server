--liquibase formatted sql

--changeset kodality:map_set
drop table if exists map_set;
create table map_set (
    id                  text                primary key,
    names               jsonb               not null,
    description         text,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null
);
select core.create_table_metadata('map_set');
--rollback drop table if exists map_set;

--changeset kodality:map_set_version
drop table if exists map_set_version;
create table map_set_version (
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
    previous_version_id bigint,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint map_set_version_ukey unique (map_set, version),
    constraint map_set_version_map_set_fk foreign key (map_set) references map_set(id),
    constraint map_set_version_previous_version_fk foreign key (previous_version_id) references map_set_version(id)
);

create index map_set_version_map_set_idx on map_set_version(map_set);

select core.create_table_metadata('map_set_version');
--rollback drop table if exists map_set_version;

--changeset kodality:map_set_entity
drop table if exists map_set_entity;
create table map_set_entity (
    id                  bigint      default nextval('core.s_entity') primary key,
    map_set             text                      not null,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint map_set_entity_map_set_fk foreign key (map_set) references map_set(id)
);

create index map_set_entity_map_set_idx on map_set_entity(map_set);

select core.create_table_metadata('map_set_entity');
--rollback drop table if exists map_set_entity;

--changeset kodality:map_set_entity_version
drop table if exists map_set_entity_version;
create table map_set_entity_version (
    id                      bigint      default nextval('core.s_entity') primary key,
    map_set_entity_id       bigint                    not null,
    description             text,
    status                  text                      not null,
    created                 timestamptz default now() not null,
    previous_version_id     bigint,
    sys_created_at          timestamp                 not null,
    sys_created_by          text                      not null,
    sys_modified_at         timestamp                 not null,
    sys_modified_by         text                      not null,
    sys_status              char(1)     default 'A'   not null collate "C",
    sys_version             int                       not null,
    constraint map_set_entity_version_map_set_entity_fk foreign key (map_set_entity_id) references map_set_entity(id),
    constraint map_set_entity_version_previous_version_fk foreign key (previous_version_id) references map_set_entity_version(id)
);

create index map_set_entity_version_map_set_entity_idx on map_set_entity_version(map_set_entity_id);
create index map_set_entity_version_previous_version_idx on map_set_entity_version(previous_version_id);

select core.create_table_metadata('map_set_entity_version');
--rollback drop table if exists map_set_entity_version;

--changeset kodality:entity_version_code_system_version_membership
drop table if exists entity_version_map_set_version_membership;
create table entity_version_map_set_version_membership (
    id                              bigserial                 not null primary key,
    map_set_entity_version_id       bigint                    not null,
    map_set_version_id              bigint                    not null,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint entity_version_ms_version_membership_ms_entity_version_fk foreign key (map_set_entity_version_id) references map_set_entity_version(id),
    constraint entity_version_ms_version_membership_ms_version_fk foreign key (map_set_version_id) references map_set_version(id)
);

select core.create_table_metadata('entity_version_map_set_version_membership');
--rollback drop table if exists entity_version_map_set_version_membership;
