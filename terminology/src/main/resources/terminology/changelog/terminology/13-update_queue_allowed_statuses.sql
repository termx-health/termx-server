--liquibase formatted sql

--changeset termx:code_system_entity_version_update_queue_allowed_statuses
alter table terminology.code_system_entity_version_update_queue
    add column if not exists allowed_statuses text;
--rollback alter table terminology.code_system_entity_version_update_queue drop column if exists allowed_statuses;
