--liquibase formatted sql

--changeset kodality:code_system_supplement
drop table if exists code_system_supplement;
create table code_system_supplement (
    id                          bigint default nextval('core.s_entity') primary key,
    code_system                 text                        not null,
    target_id                   bigint                      not null,
    target_type                 text                        not null, -- Property, PropertyValue, Designation
    description                 text,
    created                     timestamptz default now()   not null,
    sys_created_at              timestamp                   not null,
    sys_created_by              text                        not null,
    sys_modified_at             timestamp                   not null,
    sys_modified_by             text                        not null,
    sys_status                  char(1) default 'A'         not null collate "C",
    sys_version                 int                         not null,
    constraint code_system_supplement_code_system_fk foreign key (code_system) references code_system(id)
);
create index code_system_supplement_code_system_idx on code_system_supplement(code_system);


select core.create_table_metadata('code_system_supplement');
--rollback drop table if exists code_system_supplement;
