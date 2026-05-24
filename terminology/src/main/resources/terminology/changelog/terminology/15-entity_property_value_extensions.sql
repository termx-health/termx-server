--liquibase formatted sql

--changeset termx:entity_property_value-extensions
alter table terminology.entity_property_value add column if not exists extensions jsonb;
--
