--liquibase formatted sql

--changeset termx:snomed_import_tracking_details
-- Add a text[] column to capture the lifecycle log lines an admin sees in the
-- post-import email under "Success Messages" — same shape as the LoincService
-- ImportLog.successes treatment introduced in PR #138. SnomedRF2ImportFromArchiveService
-- and SnomedImportPollingService append entries here as the import moves through its
-- phases (createImportJob → uploadRF2File → Snowstorm processing → terminal status).
-- Default empty array so existing rows behave like brand-new ones with no details.
alter table sys.snomed_import_tracking
  add column if not exists details text[] not null default '{}';
