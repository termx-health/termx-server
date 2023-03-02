--liquibase formatted sql

--changeset kodality:terminology_server
drop table if exists terminology.terminology_server;

create table terminology.terminology_server (
    id                    bigint default nextval('core.s_entity') primary key,
    code                  text not null,
    names                 jsonb not null,
    root_url              text not null,
    active                boolean not null default false,
    current_installation  boolean not null default false,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null
);
create unique index terminology_server_ukey on terminology.terminology_server (code) where (sys_status = 'A');

select core.create_table_metadata('terminology.terminology_server');
--
