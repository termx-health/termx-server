--liquibase formatted sql

--changeset ig:implementation_guide
drop table if exists sys.implementation_guide_resource;
drop table if exists sys.implementation_guide_group;
drop table if exists sys.implementation_guide_version;
drop table if exists sys.implementation_guide;

create table sys.implementation_guide (
    id                  text                primary key,
    uri                 text                not null,
    publisher           text,
    name                text,
    title               jsonb               not null,
    description         jsonb,
    purpose             jsonb,
    licence             text,
    experimental        boolean,
    identifiers         jsonb,
    contacts            jsonb,
    copyright           jsonb,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null
);
create unique index implementation_guide_ukey on sys.implementation_guide (uri) where (sys_status = 'A');


create table sys.implementation_guide_version (
    id                      bigint      default nextval('core.s_entity') primary key,
    implementation_guide    text                      not null,
    version                 text                      not null,
    status                  text                      not null,
    package_id              text,
    fhir_version            text,
    github_url              text,
    empty_github_url        text,
    template                text,
    algorithm               text,
    depends_on              jsonb,
    sys_created_at          timestamp                 not null,
    sys_created_by          text                      not null,
    sys_modified_at         timestamp                 not null,
    sys_modified_by         text                      not null,
    sys_status              char(1)     default 'A'   not null collate "C",
    sys_version             int                       not null,
    constraint implementation_guide_version_implementation_guide_fk foreign key (implementation_guide) references sys.implementation_guide(id) ON UPDATE CASCADE
);
create index implementation_guide_version_implementation_guide_idx on sys.implementation_guide_version(implementation_guide);
create unique index implementation_guide_version_ukey on sys.implementation_guide_version (implementation_guide, version) where (sys_status = 'A');


create table sys.implementation_guide_group (
    id                              bigint      default nextval('core.s_entity') primary key,
    implementation_guide            text                      not null,
    implementation_guide_version_id bigint                    not null,
    name                            text                      not null,
    description                     jsonb,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint implementation_guide_group_implementation_guide_fk foreign key (implementation_guide) references sys.implementation_guide(id) ON UPDATE CASCADE,
    constraint implementation_guide_group_implementation_guide_version_fk foreign key (implementation_guide_version_id) references sys.implementation_guide_version(id)
);
create index implementation_guide_group_implementation_guide_idx on sys.implementation_guide_group(implementation_guide);
create index implementation_guide_group_implementation_guide_version_idx on sys.implementation_guide_group(implementation_guide_version_id);
create unique index implementation_guide_group_ukey on sys.implementation_guide_group (implementation_guide_version_id, name) where (sys_status = 'A');


create table sys.implementation_guide_resource (
    id                              bigint      default nextval('core.s_entity') primary key,
    implementation_guide            text                      not null,
    implementation_guide_version_id bigint                    not null,
    type                            text                      not null,
    reference                       text                      not null,
    version                         text,
    name                            text,
    group_id                        bigint                    not null,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint implementation_guide_resource_implementation_guide_fk foreign key (implementation_guide) references sys.implementation_guide(id) ON UPDATE CASCADE,
    constraint implementation_guide_resource_implementation_guide_version_fk foreign key (implementation_guide_version_id) references sys.implementation_guide_version(id),
    constraint implementation_guide_resource_group_fk foreign key (group_id) references sys.implementation_guide_group(id)
);
create index implementation_guide_resource_implementation_guide_idx on sys.implementation_guide_resource(implementation_guide);
create index implementation_guide_resource_implementation_guide_version_idx on sys.implementation_guide_resource(implementation_guide_version_id);
create index implementation_guide_resource_group_idx on sys.implementation_guide_resource(group_id);

select core.create_table_metadata('sys.implementation_guide');
select core.create_table_metadata('sys.implementation_guide_version');
select core.create_table_metadata('sys.implementation_guide_group');
select core.create_table_metadata('sys.implementation_guide_resource');
--rollback drop table if exists sys.implementation_guide; drop table if exists sys.implementation_guide_version; drop table if exists sys.implementation_guide_group; drop table if exists sys.implementation_guide_resource;
