--liquibase formatted sql

--changeset kodality:drop_measurement_unit_mapping
drop table if exists ucum.measurement_unit_mapping;
--rollback select 1;

--changeset kodality:drop_measurement_unit
drop table if exists ucum.measurement_unit;
--rollback select 1;
