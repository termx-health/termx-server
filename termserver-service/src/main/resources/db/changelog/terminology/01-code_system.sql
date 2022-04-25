--liquibase formatted sql

--changeset kodality:code_system
drop table if exists code_system;
create table code_system (
    id                  text                primary key,
    uri                 text                not null,
    names               jsonb               not null,
    description         text,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null
);

select core.create_table_metadata('code_system');
--rollback drop table if exists code_system;

--changeset kodality:code_system_version
drop table if exists code_system_version;
create table code_system_version (
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
    previous_version_id bigint,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint code_system_version_ukey unique (code_system, version),
    constraint code_system_version_code_system_fk foreign key (code_system) references code_system(id),
    constraint code_system_version_previous_version_fk foreign key (previous_version_id) references code_system_version(id)
);
create index code_system_version_code_system_idx on code_system_version(code_system);

select core.create_table_metadata('code_system_version');
--rollback drop table if exists code_system_version;
