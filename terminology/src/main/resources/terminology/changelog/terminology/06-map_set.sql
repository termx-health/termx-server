--liquibase formatted sql

--changeset kodality:map_set-1
drop table if exists terminology.map_set_association;
drop table if exists terminology.entity_version_map_set_version_membership;
drop table if exists terminology.map_set_version;
drop table if exists terminology.map_set_entity_version;
drop table if exists terminology.map_set_entity;
drop table if exists terminology.map_set;
create table terminology.map_set (
    id                  text                primary key,
    uri                 text,
    publisher           text,
    name                text,
    title               jsonb               not null,
    description         jsonb,
    purpose             jsonb,
    narrative           text,
    experimental        boolean,
    identifiers         jsonb,
    contacts            jsonb,
    copyright           jsonb,
    settings            jsonb,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null
);
create unique index map_set_ukey on terminology.map_set (uri) where (sys_status = 'A');


create table terminology.map_set_version (
    id                  bigint      default nextval('core.s_entity') primary key,
    map_set             text                      not null,
    version             text                      not null,
    status              text                      not null,
    preferred_language  text,
    description         jsonb,
    algorithm           text,
    release_date        timestamp                 not null,
    expiration_date     timestamp,
    created             timestamptz default now() not null,
    scope               jsonb                     not null,
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


select core.create_table_metadata('terminology.map_set');
select core.create_table_metadata('terminology.map_set_version');
--rollback drop table if exists terminology.map_set; drop table if exists terminology.map_set_version;


--changeset kodality:map_set_statistics
drop table if exists terminology.map_set_statistics;
create table terminology.map_set_statistics (
    id                      bigserial               not null primary key,
    map_set                 text                    not null,
    map_set_version_id      bigint                  not null,
    source_concepts         integer                 not null,
    equivalent              integer                 not null,
    no_map                  integer                 not null,
    narrower                integer                 not null,
    broader                 integer                 not null,
    unmapped                integer                 not null,
    inactive_sources        integer                 not null,
    inactive_targets        integer                 not null,
    created_at              timestamptz             not null,
    created_by              text,
    sys_created_at          timestamp               not null,
    sys_created_by          text                    not null,
    sys_modified_at         timestamp               not null,
    sys_modified_by         text                    not null,
    sys_status              char(1) default 'A'     not null collate "C",
    sys_version             int                     not null,
    constraint map_set_statistics_map_set_fk foreign key (map_set) references terminology.map_set(id),
    constraint map_set_statistics_map_set_version_fk foreign key (map_set_version_id) references terminology.map_set_version(id)
);
create index map_set_statistics_map_set_idx on terminology.map_set_statistics(map_set);
create index map_set_statistics_map_set_version_idx on terminology.map_set_statistics(map_set_version_id);
create unique index map_set_statistics_map_set_version_id ON terminology.map_set_statistics(map_set_version_id) WHERE sys_status = 'A';

select core.create_table_metadata('terminology.map_set_statistics');
--rollback drop table if exists terminology.map_set_statistics;

--changeset kodality:map_set_property
drop table if exists terminology.map_set_property;
create table terminology.map_set_property (
    id                          bigint      default nextval('core.s_entity') primary key,
    map_set                     text                      not null,
    name                        text                      not null,
    type                        text                      not null,
    uri                         text,
    description                 jsonb,
    rule                        jsonb,
    status                      text                      not null,
    created                     timestamptz default now() not null,
    order_number                smallint,
    required                    boolean,
    defined_entity_property_id  bigint,
    sys_created_at              timestamp                 not null,
    sys_created_by              text                      not null,
    sys_modified_at             timestamp                 not null,
    sys_modified_by             text                      not null,
    sys_status                  char(1)     default 'A'   not null collate "C",
    sys_version                 int                       not null,
    constraint map_set_property_map_set_fk foreign key (map_set) references terminology.map_set(id),
    constraint map_set_property_defined_property_fk foreign key (defined_entity_property_id) references terminology.defined_entity_property(id)
);
create index map_set_property_map_set_idx on terminology.map_set_property(map_set);
create index map_set_property_defined_property_idx on terminology.map_set_property(defined_entity_property_id);

select core.create_table_metadata('terminology.map_set_property');
--rollback drop table if exists terminology.map_set_property;

--changeset kodality:map_set_version-identifiers
alter table terminology.map_set_version add column identifiers jsonb;
--
