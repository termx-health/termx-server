--liquibase formatted sql

--changeset termx:taskforge.project-data-1
with vals (code, names) as (values
  ('termx', '{"en": "TermX"}'::jsonb)
),
ins as (
  insert into taskforge.project (institution, code, names)
  select '1', code, names from vals
  where not exists (select 1 from taskforge.project where institution = '1' and code = vals.code)
  returning id
),
upd as (
  update taskforge.project
  set names = v.names
  from vals v
  where v.code = project.code and project.institution = '1'
  returning id
),
acl_consume as (
  insert into core.acl(s_id, tenant, access)
  select id, null, 'consume' from ins
  where not core.aclchk(id, null, 'consume')
  returning 1
)
select * from ins, acl_consume;
--

--changeset termx:taskforge.workflow-data-2
with vals(project_id, task_type, transitions) as (values
  ((select id from taskforge.project where institution = '1' and code = 'termx'),
  'version-review',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskforge.project where institution = '1' and code = 'termx'),
  'version-approval',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskforge.project where institution = '1' and code = 'termx'),
  'concept-review',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskforge.project where institution = '1' and code = 'termx'),
  'concept-approval',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskforge.project where institution = '1' and code = 'termx'),
  'wiki-page-comment',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "completed"}'::jsonb,
    '{"from": "accepted", "to": "completed"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb
  ))
)
insert into taskforge.workflow(project_id, task_type, transitions)
select vals.project_id, vals.task_type, vals.transitions from vals
where not exists (select 1 from taskforge.workflow where task_type = vals.task_type and project_id = vals.project_id);
--

--changeset termx:taskforge.workflow-data-3
with vals(project_id, task_type, transitions) as (values
  ((select id from taskforge.project where institution = '1' and code = 'termx'),
  'valueset-review',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskforge.project where institution = '1' and code = 'termx'),
  'conceptmap-review',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskforge.project where institution = '1' and code = 'termx'),
  'wiki-task',
  jsonb_build_array(
    '{"from": null, "to": "draft"}'::jsonb,
    '{"from": "draft", "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "in-progress"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "in-progress", "to": "completed"}'::jsonb,
    '{"from": "in-progress", "to": "on-hold"}'::jsonb,
    '{"from": "in-progress", "to": "cancelled"}'::jsonb,
    '{"from": "on-hold", "to": "in-progress"}'::jsonb,
    '{"from": "on-hold", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  ))
)
insert into taskforge.workflow(project_id, task_type, transitions)
select vals.project_id, vals.task_type, vals.transitions from vals
where not exists (select 1 from taskforge.workflow where task_type = vals.task_type and project_id = vals.project_id);
--
