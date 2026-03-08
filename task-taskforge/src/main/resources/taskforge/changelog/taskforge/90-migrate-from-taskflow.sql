--liquibase formatted sql

-- =============================================================================
-- Migration script: taskflow -> taskforge
--
-- Copies all data from the old taskflow schema into the new taskforge schema,
-- preserving original IDs. After migration, resets identity sequences so new
-- inserts get IDs beyond the migrated range.
--
-- To use: uncomment the include line in changelog.xml
-- =============================================================================

--changeset termx:migrate-project runOnChange:false
insert into taskforge.project (id, institution, code, names, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by)
select id, institution, code, names, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by
from taskflow.project
where not exists (select 1 from taskforge.project tp where tp.id = taskflow.project.id)
on conflict do nothing;
--

--changeset termx:migrate-workflow runOnChange:false
insert into taskforge.workflow (id, project_id, task_type, transitions, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by)
select id, project_id, task_type, transitions, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by
from taskflow.workflow
where not exists (select 1 from taskforge.workflow tw where tw.id = taskflow.workflow.id)
on conflict do nothing;
--

--changeset termx:migrate-task runOnChange:false
insert into taskforge.task (id, project_id, workflow_id, parent_id, number, type, status, business_status, priority, created_by, created_at, updated_at, updated_by, assignee, title, content, context, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by)
select id, project_id, workflow_id, parent_id, number, type, status, business_status, priority, created_by, created_at, updated_at, updated_by, assignee, title, content, context, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by
from taskflow.task
where not exists (select 1 from taskforge.task tt where tt.id = taskflow.task.id)
on conflict do nothing;
--

--changeset termx:migrate-task_activity runOnChange:false
insert into taskforge.task_activity (id, task_id, note, transition, context, updated_by, updated_at, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by)
select id, task_id, note, transition, context, updated_by, updated_at, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by
from taskflow.task_activity
where not exists (select 1 from taskforge.task_activity ta where ta.id = taskflow.task_activity.id)
on conflict do nothing;
--

--changeset termx:migrate-task_attachment runOnChange:false
insert into taskforge.task_attachment (id, task_id, file_id, file_name, description, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by, sys_status, sys_version)
select id, task_id, file_id, file_name, description, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by, sys_status, sys_version
from taskflow.task_attachment
where not exists (select 1 from taskforge.task_attachment ta where ta.id = taskflow.task_attachment.id)
on conflict do nothing;
--

--changeset termx:migrate-task_execution runOnChange:false
insert into taskforge.task_execution (id, task_id, period_start, period_end, duration, performer, created_by, created_at, updated_at, updated_by, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by)
select id, task_id, period_start, period_end, duration, performer, created_by, created_at, updated_at, updated_by, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by
from taskflow.task_execution
where not exists (select 1 from taskforge.task_execution te where te.id = taskflow.task_execution.id)
on conflict do nothing;
--

--changeset termx:migrate-project-acl runOnChange:false
insert into core.acl (s_id, tenant, access)
select distinct s_id, tenant, access
from core.acl as a1
where not exists (
    select 1 
    from taskflow.project p 
    where p.id = a1.s_id
);
--


-- Reset identity sequences so new rows get IDs beyond the migrated data.
-- Tables using identity columns: task_activity, task_attachment, task_execution

--changeset termx:migrate-reset-sequences runOnChange:false
select setval(pg_get_serial_sequence('taskforge.task_activity', 'id'), coalesce((select max(id) from taskforge.task_activity), 0) + 1, false);
select setval(pg_get_serial_sequence('taskforge.task_attachment', 'id'), coalesce((select max(id) from taskforge.task_attachment), 0) + 1, false);
select setval(pg_get_serial_sequence('taskforge.task_execution', 'id'), coalesce((select max(id) from taskforge.task_execution), 0) + 1, false);
--
