--liquibase formatted sql

--changeset termx:project
create table taskforge.project (
  id                    bigint default nextval('core.seq_id') not null,
  institution           text,
  code                  text not null COLLATE "C",
  names                 jsonb not null,
  sys_status            char(1) default 'A' not null,
  sys_version           integer not null,
  sys_created_at        timestamptz not null,
  sys_created_by        text not null,
  sys_modified_at       timestamptz,
  sys_modified_by       text,
  constraint project_pkey primary key (id),
  constraint project_ukey unique (code, institution)
);

select core.create_table_metadata('taskforge.project');
select audit.add_log('taskforge.project');
--
