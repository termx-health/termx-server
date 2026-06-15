--liquibase formatted sql

-- Backfill the FHIR versionAlgorithm (http://hl7.org/fhir/ValueSet/version-algorithm) on existing
-- CodeSystem / ValueSet / ConceptMap(MapSet) versions, inferred from the shape of the version
-- string. Each statement only fills rows where algorithm is still null, so the changesets are
-- safe to re-run and never overwrite an explicitly chosen algorithm.
--   number(.number(.number))  -> semver
--   digits only               -> integer
--   yyyy-mm-dd…               -> date
--   anything else             -> natural

--changeset termx:code_system_version-algorithm-backfill
update terminology.code_system_version
   set algorithm = case
     when version ~ '^[0-9]+$' then 'integer'
     when version ~ '^[0-9]+\.[0-9]+(\.[0-9]+)?$' then 'semver'
     when version ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' then 'date'
     else 'natural'
   end
 where algorithm is null and version is not null and sys_status = 'A';
--rollback update terminology.code_system_version set algorithm = null;

--changeset termx:value_set_version-algorithm-backfill
update terminology.value_set_version
   set algorithm = case
     when version ~ '^[0-9]+$' then 'integer'
     when version ~ '^[0-9]+\.[0-9]+(\.[0-9]+)?$' then 'semver'
     when version ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' then 'date'
     else 'natural'
   end
 where algorithm is null and version is not null and sys_status = 'A';
--rollback update terminology.value_set_version set algorithm = null;

--changeset termx:map_set_version-algorithm-backfill
update terminology.map_set_version
   set algorithm = case
     when version ~ '^[0-9]+$' then 'integer'
     when version ~ '^[0-9]+\.[0-9]+(\.[0-9]+)?$' then 'semver'
     when version ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' then 'date'
     else 'natural'
   end
 where algorithm is null and version is not null and sys_status = 'A';
--rollback update terminology.map_set_version set algorithm = null;
