--liquibase formatted sql

--changeset kodality:concept
drop table if exists concept;
create table concept (
    id              bigint              not null primary key,
    code_system     text                not null,
    code            text                not null,
    description     text,
    sys_created_at  timestamp           not null,
    sys_created_by  text                not null,
    sys_modified_at timestamp           not null,
    sys_modified_by text                not null,
    sys_status      char(1) default 'A' not null collate "C",
    sys_version     int                 not null,
    constraint concept_id_fk foreign key (id) references code_system_entity(id),
    constraint concept_code_system_code_ukey unique (code_system, code),
    constraint concept_code_system_fk foreign key (code_system) references code_system(id)
);
create index concept_code_system_idx on concept(code_system);

select core.create_table_metadata('concept');
--rollback drop table if exists concept;
