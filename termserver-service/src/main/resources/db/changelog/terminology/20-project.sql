--liquibase formatted sql

--changeset kodality:project
drop table if exists terminology.package_version_resource;
drop table if exists terminology.package_version;
drop table if exists terminology.package;
drop table if exists terminology.project;

create table terminology.project (
    id                    bigint default nextval('core.s_entity') primary key,
    code                  text not null,
    names                 jsonb not null,
    active                boolean not null default false,
    shared                boolean not null default false,
    acl                   jsonb,
    terminology_servers   jsonb,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null
);
create unique index project_ukey on terminology.project (code) where (sys_status = 'A');

select core.create_table_metadata('terminology.project');
--
