--liquibase formatted sql

--changeset termx:closure
drop table if exists terminology.closure_relationship;
drop table if exists terminology.closure_concept;
drop table if exists terminology.closure;

create table terminology.closure (
    id              bigint              default nextval('core.s_entity') primary key,
    name            text                not null,
    current_version int                 not null default 0,
    sys_created_at  timestamp           not null,
    sys_created_by  text                not null,
    sys_modified_at timestamp           not null,
    sys_modified_by text                not null,
    sys_status      char(1) default 'A' not null collate "C",
    sys_version     int                 not null
);
create unique index closure_name_ukey on terminology.closure (name) where (sys_status = 'A');
select core.create_table_metadata('terminology.closure');

create table terminology.closure_concept (
    id              bigint              default nextval('core.s_entity') primary key,
    closure_id      bigint              not null,
    code_system     text                not null,
    code            text                not null,
    version         int                 not null,
    sys_created_at  timestamp           not null,
    sys_created_by  text                not null,
    sys_modified_at timestamp           not null,
    sys_modified_by text                not null,
    sys_status      char(1) default 'A' not null collate "C",
    sys_version     int                 not null,
    constraint closure_concept_closure_fk foreign key (closure_id) references terminology.closure(id)
);
create unique index closure_concept_ukey on terminology.closure_concept (closure_id, code_system, code) where (sys_status = 'A');
create index closure_concept_closure_idx on terminology.closure_concept (closure_id);
select core.create_table_metadata('terminology.closure_concept');

create table terminology.closure_relationship (
    id              bigint              default nextval('core.s_entity') primary key,
    closure_id      bigint              not null,
    code_system     text                not null,
    child_code      text                not null,
    parent_code     text                not null,
    version         int                 not null,
    sys_created_at  timestamp           not null,
    sys_created_by  text                not null,
    sys_modified_at timestamp           not null,
    sys_modified_by text                not null,
    sys_status      char(1) default 'A' not null collate "C",
    sys_version     int                 not null,
    constraint closure_relationship_closure_fk foreign key (closure_id) references terminology.closure(id)
);
create unique index closure_relationship_ukey on terminology.closure_relationship (closure_id, code_system, child_code, parent_code) where (sys_status = 'A');
create index closure_relationship_closure_version_idx on terminology.closure_relationship (closure_id, version);
select core.create_table_metadata('terminology.closure_relationship');

--rollback drop table if exists terminology.closure_relationship;
--rollback drop table if exists terminology.closure_concept;
--rollback drop table if exists terminology.closure;
