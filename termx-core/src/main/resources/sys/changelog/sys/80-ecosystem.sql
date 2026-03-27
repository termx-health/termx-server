--liquibase formatted sql

--changeset termx:ecosystem-drop
drop table if exists sys.ecosystem;
--

--changeset termx:ecosystem-v2
create table sys.ecosystem (
    id              bigint default nextval('core.s_entity') not null,
    code            text not null,
    names           jsonb,
    format_version  text not null default '1',
    description     text,
    active          boolean not null default true,
    sys_status      char(1) default 'A' not null collate "C",
    sys_version     integer not null,
    sys_created_at  timestamptz not null,
    sys_created_by  text not null,
    sys_modified_at timestamptz not null,
    sys_modified_by text not null,
    constraint ecosystem_pk primary key (id)
);
create unique index ecosystem_ukey on sys.ecosystem (code) where (sys_status = 'A');
select core.create_table_metadata('sys.ecosystem');
--rollback drop table sys.ecosystem;

--changeset termx:ecosystem-server
create table sys.ecosystem_server (
    id              bigserial not null,
    ecosystem_id    bigint not null,
    server_id       bigint not null,
    sys_status      char(1) default 'A' not null collate "C",
    sys_version     integer not null,
    sys_created_at  timestamptz not null,
    sys_created_by  text not null,
    sys_modified_at timestamptz not null,
    sys_modified_by text not null,
    constraint ecosystem_server_pk primary key (id),
    constraint ecosystem_server_ecosystem_fk foreign key (ecosystem_id) references sys.ecosystem(id),
    constraint ecosystem_server_server_fk foreign key (server_id) references sys.terminology_server(id)
);
create index ecosystem_server_ecosystem_idx on sys.ecosystem_server (ecosystem_id);
create index ecosystem_server_server_idx on sys.ecosystem_server (server_id);
create unique index ecosystem_server_ukey on sys.ecosystem_server (ecosystem_id, server_id) where (sys_status = 'A');
select core.create_table_metadata('sys.ecosystem_server');
--rollback drop table sys.ecosystem_server;
