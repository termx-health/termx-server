--liquibase formatted sql

--changeset termx:code_system_update_external_web_source
alter table terminology.code_system
drop column if exists external_web_source,
add column external_web_source text;

--rollback  alter table code_system
--rollback  drop column if exists external_web_source,
--rollback  add column external_web_source boolean;