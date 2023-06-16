--liquibase formatted sql

--changeset kodality:taskflow.space-data
INSERT INTO taskflow.space (institution, code, names)
select '1', 'kts', '{"en": "Kodality Terminology Service"}'::jsonb
where not exists (select 1 from taskflow.space where institution = '1' and code = 'kts');
--

--changeset kodality:taskflow.workflow-data
with t(space_id, task_type, transitions) as (
  select
    (select id from taskflow.space where institution = '1' and code = 'kts'),
    'snomed-translation-validation',
    jsonb_build_array(
      '{"from": null, "to": "draft"}'::jsonb,
      '{"from": null, "to": "requested"}'::jsonb,
      '{"from": "draft", "to": "requested"}'::jsonb,
      '{"from": "draft", "to": "cancelled"}'::jsonb,
      '{"from": "requested", "to": "received"}'::jsonb,
      '{"from": "requested", "to": "accepted"}'::jsonb,
      '{"from": "requested", "to": "rejected"}'::jsonb,
      '{"from": "requested", "to": "failed"}'::jsonb,
      '{"from": "requested", "to": "cancelled"}'::jsonb,
      '{"from": "received", "to": "accepted"}'::jsonb,
      '{"from": "received", "to": "rejected"}'::jsonb,
      '{"from": "received", "to": "failed"}'::jsonb,
      '{"from": "received", "to": "cancelled"}'::jsonb,
      '{"from": "rejected", "to": "draft"}'::jsonb,
      '{"from": "rejected", "to": "received"}'::jsonb,
      '{"from": "rejected", "to": "cancelled"}'::jsonb,
      '{"from": "failed", "to": "draft"}'::jsonb,
      '{"from": "failed", "to": "in-progress"}'::jsonb,
      '{"from": "accepted", "to": "failed"}'::jsonb,
      '{"from": "accepted", "to": "cancelled"}'::jsonb,
      '{"from": "in-progress", "to": "requested"}'::jsonb,
      '{"from": "in-progress", "to": "cancelled"}'::jsonb,
      '{"from": "cancelled", "to": "draft"}'::jsonb
    )
)
INSERT INTO taskflow.workflow(space_id, task_type, transitions)
select t.space_id, t.task_type, t.transitions from t
where not exists (select 1 from taskflow.workflow where task_type = t.task_type and space_id = t.space_id);
--
