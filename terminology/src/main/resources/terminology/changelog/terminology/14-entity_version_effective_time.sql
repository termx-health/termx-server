--liquibase formatted sql

--changeset kodality:code_system_entity_version_effective_time
alter table terminology.code_system_entity_version
    add column if not exists effective_time text;
--rollback alter table terminology.code_system_entity_version drop column if exists effective_time;
