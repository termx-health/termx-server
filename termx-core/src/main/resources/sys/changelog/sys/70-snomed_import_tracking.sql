--liquibase formatted sql

--changeset termx:snomed_import_tracking
create table if not exists sys.snomed_import_tracking (
  id                 bigserial primary key,
  snowstorm_job_id   text not null unique,
  branch_path        text,
  type               text,
  status             text not null,
  started            timestamptz not null default current_timestamp,
  finished           timestamptz,
  error_message      text,
  notified           boolean default false,
  sys_created_at     timestamptz not null default current_timestamp,
  sys_created_by     text not null default 'system'
);

create index if not exists snomed_import_tracking_status_idx on sys.snomed_import_tracking(status) where status = 'RUNNING';
create index if not exists snomed_import_tracking_notified_idx on sys.snomed_import_tracking(notified) where notified = false;
