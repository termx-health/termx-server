--liquibase formatted sql

--changeset termx:code_system_version-hierarchy_shape
-- How a version's concept hierarchy is written in its FHIR resource. NULL means "no setting": the
-- server derives one that is both lossless and valid for the data (nested for a tree, flat for a
-- DAG, where nesting would repeat codes and break CSD-1). Constrained here so a bad value cannot
-- reach the exporter, which would otherwise have to treat an unknown string as one of the two.
alter table terminology.code_system_version add column if not exists hierarchy_shape text;
alter table terminology.code_system_version drop constraint if exists code_system_version_hierarchy_shape_chk;
alter table terminology.code_system_version add constraint code_system_version_hierarchy_shape_chk
    check (hierarchy_shape is null or hierarchy_shape in ('nested', 'flat'));
--
