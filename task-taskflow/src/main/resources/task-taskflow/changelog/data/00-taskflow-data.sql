--liquibase formatted sql

--changeset kodality:taskflow.project-data-1
with vals (code, names) as (values
  ('termx', '{"en": "TermX"}'::jsonb)
),
ins as (
  insert into taskflow.project (institution, code, names)
  select '1', code, names from vals
  where not exists (select 1 from taskflow.project where institution = '1' and code = vals.code)
  returning id
),
upd as (
  update taskflow.project
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

--changeset kodality:taskflow.workflow-data-2
with vals(project_id, task_type, transitions) as (values
  ((select id from taskflow.project where institution = '1' and code = 'termx'),
  'version-review',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskflow.project where institution = '1' and code = 'termx'),
  'version-approval',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskflow.project where institution = '1' and code = 'termx'),
  'concept-review',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskflow.project where institution = '1' and code = 'termx'),
  'concept-approval',
  jsonb_build_array(
    '{"from": null, "to": "requested"}'::jsonb,
    '{"from": "requested", "to": "accepted"}'::jsonb,
    '{"from": "requested", "to": "rejected"}'::jsonb,
    '{"from": "requested", "to": "cancelled"}'::jsonb,
    '{"from": "accepted", "to": "cancelled"}'::jsonb,
    '{"from": "rejected", "to": "cancelled"}'::jsonb
  )),

  ((select id from taskflow.project where institution = '1' and code = 'termx'),
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
insert into taskflow.workflow(project_id, task_type, transitions)
select vals.project_id, vals.task_type, vals.transitions from vals
where not exists (select 1 from taskflow.workflow where task_type = vals.task_type and project_id = vals.project_id);
--
