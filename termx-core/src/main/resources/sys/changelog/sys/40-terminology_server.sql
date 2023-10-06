--liquibase formatted sql

--changeset kodality:terminology_server
drop table if exists sys.terminology_server;

create table sys.terminology_server (
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
create unique index terminology_server_ukey on sys.terminology_server (code) where (sys_status = 'A');

select core.create_table_metadata('sys.terminology_server');
--

--changeset kodality:terminology_server-kind
alter table sys.terminology_server add column kind jsonb;
update sys.terminology_server set kind = '["terminology"]'::jsonb;
alter table sys.terminology_server alter column kind set not null;
--

--changeset kodality:terminology_server-headers
alter table sys.terminology_server add column headers jsonb;
--

--changeset kodality:terminology_server-auth
alter table sys.terminology_server add column auth_config jsonb;
--

