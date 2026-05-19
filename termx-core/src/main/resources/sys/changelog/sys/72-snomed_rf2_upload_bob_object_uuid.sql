--liquibase formatted sql

--changeset termx:snomed_rf2_upload-bob-object-uuid
-- Lets a dry-run scan that came from a Bob-stored archive skip the local zip_data buffer
-- entirely. The legacy /imports/scan path still populates zip_data; the new from-archive
-- path populates bob_object_uuid and leaves zip_data NULL.
alter table sys.snomed_rf2_upload add column if not exists bob_object_uuid text;
alter table sys.snomed_rf2_upload alter column zip_data drop not null;
create index if not exists snomed_rf2_upload_bob_object_uuid_idx
    on sys.snomed_rf2_upload(bob_object_uuid)
    where bob_object_uuid is not null;
--rollback alter table sys.snomed_rf2_upload drop column if exists bob_object_uuid;
--rollback alter table sys.snomed_rf2_upload alter column zip_data set not null;
--
