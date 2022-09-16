--liquibase formatted sql

--changeset kodality:naming_system
drop table if exists terminology.naming_system;
create table terminology.naming_system (
    id                          text                        primary key,
    names                       jsonb                       not null,
    kind                        text                        not null,
    source                      text,
    description                 text,
    identifiers                 jsonb                       not null,
    code_system                 text,
    status                      text                        not null,
    created                     timestamptz default now()   not null,
    sys_created_at              timestamp                   not null,
    sys_created_by              text                        not null,
    sys_modified_at             timestamp                   not null,
    sys_modified_by             text                        not null,
    sys_status                  char(1) default 'A'         not null collate "C",
    sys_version                 int                         not null,
    constraint naming_system_code_system_fk foreign key (code_system) references terminology.code_system(id)
);
create index naming_system_code_system_idx on terminology.naming_system(code_system);

select core.create_table_metadata('terminology.naming_system');
--rollback drop table if exists terminology.naming_system;
