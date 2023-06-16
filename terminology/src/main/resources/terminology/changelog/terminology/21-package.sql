--liquibase formatted sql

--changeset kodality:package-2
drop table if exists terminology.package_version_resource;
drop table if exists terminology.package_version;
drop table if exists terminology.package;

create table terminology.package (
    id                    bigint default nextval('core.s_entity') primary key,
    space_id              bigint not null,
    code                  text not null,
    status                text not null,
    git                   text,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null,
    constraint package_space_fkey foreign key (space_id) references terminology.space (id)
);
create index package_space_idx on terminology.package (space_id);
create unique index package_ukey on terminology.package(code) where (sys_status = 'A');

create table terminology.package_version (
    id                    bigserial not null,
    package_id            bigint not null,
    version               text not null,
    description           text,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null,
    constraint package_version_package_fkey foreign key (package_id) references terminology.package (id)
);
create index package_version_package_idx on terminology.package_version (package_id);
create unique index package_version_ukey on terminology.package_version(package_id, version) where (sys_status = 'A');

create table terminology.package_version_resource (
    id                    bigserial not null,
    version_id            bigint not null,
    resource_id           text not null,
    resource_type         text not null,
    terminology_server    text,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null
);
create unique index package_version_resource_resource_idx on terminology.package_version_resource (version_id, resource_type, resource_id) where (sys_status = 'A');

select core.create_table_metadata('terminology.package');
select core.create_table_metadata('terminology.package_version');

select core.create_table_metadata('terminology.package_version_resource');
--
