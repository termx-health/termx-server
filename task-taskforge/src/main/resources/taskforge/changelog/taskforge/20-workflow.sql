--liquibase formatted sql

--changeset termx:workflow-1
create table taskforge.workflow (
  id                    bigint default nextval('core.seq_id') not null,
  project_id            bigint not null,
  task_type             text not null,
  transitions           jsonb not null,
  sys_status            char(1) default 'A' not null,
  sys_version           integer not null,
  sys_created_at        timestamptz not null,
  sys_created_by        text not null,
  sys_modified_at       timestamptz,
  sys_modified_by       text,
  constraint workflow_pkey primary key (id),
  constraint workflow_ukey unique (project_id, task_type),
  constraint workflow_project_fkey foreign key (project_id) references taskforge.project (id)
);

select core.create_table_metadata('taskforge.workflow');
select audit.add_log('taskforge.workflow');
--

