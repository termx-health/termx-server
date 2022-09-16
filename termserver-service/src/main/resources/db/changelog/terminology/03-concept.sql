--liquibase formatted sql

--changeset kodality:concept
drop table if exists terminology.concept;
create table terminology.concept (
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
    constraint concept_id_fk foreign key (id) references terminology.code_system_entity(id),
    constraint concept_code_system_fk foreign key (code_system) references terminology.code_system(id)
);
create unique index concept_code_system_code_ukey on terminology.concept (code_system, code) where (sys_status = 'A');
create index concept_code_system_idx on terminology.concept(code_system);

select core.create_table_metadata('terminology.concept');
--rollback drop table if exists terminology.concept;
