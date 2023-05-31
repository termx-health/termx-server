--liquibase formatted sql

--changeset kodality:lorque_process
drop table if exists sys.lorque_process;
create table sys.lorque_process (
  id                    bigserial primary key,
  process_name          text not null collate "C",
  status                text not null collate "C",
  started               timestamptz not null,
  finished              timestamptz,
  result                bytea,
  result_type           text collate "C",
  sys_created_at        timestamp not null,
  sys_created_by        text not null,
  sys_modified_at       timestamp,
  sys_modified_by       text,
  sys_status            char(1) default 'A' not null collate "C",
  sys_version           int  not null
  );

select core.create_table_metadata('sys.lorque_process');
--rollback drop table sys.lorque_process;

--changeset kodality:grant-lorque_process-delete
GRANT DELETE ON table sys.lorque_process TO ${app-username};
--rollback select 1;
