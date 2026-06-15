--liquibase formatted sql

--changeset termx:structure_definition_version-algorithm
alter table modeler.structure_definition_version add column algorithm text;
--rollback alter table modeler.structure_definition_version drop column algorithm;

-- Infer the FHIR versionAlgorithm (http://hl7.org/fhir/ValueSet/version-algorithm) from the
-- shape of each existing version string. Only fills rows where algorithm is still null, so it is
-- safe to re-run and never overwrites an explicitly chosen algorithm.
--changeset termx:structure_definition_version-algorithm-backfill
update modeler.structure_definition_version
   set algorithm = case
     when version ~ '^[0-9]+$' then 'integer'
     when version ~ '^[0-9]+\.[0-9]+(\.[0-9]+)?$' then 'semver'
     when version ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' then 'date'
     else 'natural'
   end
 where algorithm is null and version is not null and sys_status = 'A';
--rollback update modeler.structure_definition_version set algorithm = null;
