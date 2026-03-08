--liquibase formatted sql

--changeset termx:task-2
create table taskforge.task (
  id                    bigint default nextval('core.seq_id') not null,
  project_id            bigint not null,
  workflow_id           bigint not null,
  parent_id             bigint,
  number                text not null,
  type                  text not null,
  status                text not null,
  business_status       text,
  priority              text not null,
  created_by            text not null,
  created_at            timestamptz not null,
  updated_at            timestamptz,
  updated_by            text,
  assignee              text,
  title                 text not null,
  content               text,
  context               jsonb,
  sys_status            char(1) default 'A' not null,
  sys_version           integer not null,
  sys_created_at        timestamptz not null,
  sys_created_by        text not null,
  sys_modified_at       timestamptz,
  sys_modified_by       text,
  constraint task_pkey primary key (id),
  constraint task_ukey unique (number),
  constraint task_parent_fkey foreign key (parent_id) references taskforge.task (id),
  constraint task_project_fkey foreign key (project_id) references taskforge.project (id)
);

select core.create_table_metadata('taskforge.task');
select audit.add_log('taskforge.task');
--

