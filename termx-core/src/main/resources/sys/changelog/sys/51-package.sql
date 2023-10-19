--liquibase formatted sql

--changeset kodality:package
drop table if exists sys.package_version_resource;
drop table if exists sys.package_version;
drop table if exists sys.package;

create table sys.package (
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
    constraint package_space_fkey foreign key (space_id) references sys.space (id)
);
create index package_space_idx on sys.package (space_id);
create unique index package_ukey on sys.package(code) where (sys_status = 'A');

create table sys.package_version (
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
    constraint package_version_package_fkey foreign key (package_id) references sys.package (id)
);
create index package_version_package_idx on sys.package_version (package_id);
create unique index package_version_ukey on sys.package_version(package_id, version) where (sys_status = 'A');

create table sys.package_version_resource (
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
create unique index package_version_resource_resource_idx on sys.package_version_resource (version_id, resource_type, resource_id) where (sys_status = 'A');

select core.create_table_metadata('sys.package');
select core.create_table_metadata('sys.package_version');

select core.create_table_metadata('sys.package_version_resource');
--

--changeset kodality:package-remove-git
alter table sys.package drop column git;
--

--changeset kodality:package-ukey-fix
drop index sys.package_ukey;
create unique index package_ukey on sys.package(space_id, code) where (sys_status = 'A');
--
