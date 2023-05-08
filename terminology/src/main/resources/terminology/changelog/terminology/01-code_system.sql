--liquibase formatted sql

--changeset kodality:code_system
drop table if exists terminology.code_system;
create table terminology.code_system (
    id                  text                primary key,
    uri                 text,
    identifiers         jsonb,
    names               jsonb               not null,
    content             text                not null,
    base_code_system    text,
    contacts            jsonb,
    case_sensitive      text,
    narrative           text,
    description         text,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null,
    constraint code_system_base_code_system_fk foreign key (base_code_system) references terminology.code_system(id)
);
create unique index code_system_ukey on terminology.code_system (uri) where (sys_status = 'A');
create index code_system_base_code_system_idx on terminology.code_system(base_code_system);

select core.create_table_metadata('terminology.code_system');
--rollback drop table if exists terminology.code_system;

--changeset kodality:code_system_version
drop table if exists terminology.code_system_version;
create table terminology.code_system_version (
    id                  bigint      default nextval('core.s_entity') primary key,
    code_system         text                      not null,
    version             text                      not null,
    source              text,
    preferred_language  text,
    supported_languages text[],
    description         text,
    status              text                      not null,
    release_date        timestamp                 not null,
    expiration_date     timestamp,
    created             timestamptz default now() not null,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint code_system_version_code_system_fk foreign key (code_system) references terminology.code_system(id)
);
create unique index code_system_version_ukey on terminology.code_system_version (code_system, version) where (sys_status = 'A');
create index code_system_version_code_system_idx on terminology.code_system_version(code_system);

select core.create_table_metadata('terminology.code_system_version');
--rollback drop table if exists terminology.code_system_version;

--changeset kodality:code_system-supported_languages
alter table terminology.code_system add column supported_languages text[];
--

--changeset kodality:code_system-sequence
alter table terminology.code_system add column sequence text;
--

