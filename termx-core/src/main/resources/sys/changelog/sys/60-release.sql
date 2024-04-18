--liquibase formatted sql

--changeset kodality:release
drop table if exists sys.release_resource;
drop table if exists sys.release;

create table sys.release (
    id                    bigint default nextval('core.s_entity') primary key,
    code                  text not null,
    names                 jsonb not null,
    planned               timestamptz,
    release_date          timestamptz,
    status                text not null,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null
);
create unique index release_ukey on sys.release (code) where (sys_status = 'A');

create table sys.release_resource (
    id                    bigserial not null,
    release_id            bigint not null,
    resource_type         text not null,
    resource_id           text not null,
    resource_version      text,
    resource_names        jsonb,
    error_count           smallint,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null,
    constraint release_resource_release_fkey foreign key (release_id) references sys.release (id)
);
create index release_resource_release_idx on sys.release_resource (release_id);

select core.create_table_metadata('sys.release');
select core.create_table_metadata('sys.release_resource');
--


--changeset kodality:release-authors
alter table sys.release add column authors jsonb;
--
