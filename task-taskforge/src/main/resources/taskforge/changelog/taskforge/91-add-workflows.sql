--liquibase formatted sql

--changeset termx:add-valueset-conceptmap-wiki-workflows splitStatements:false
DO $$
DECLARE
  v_project_id bigint;
BEGIN
  SELECT id INTO v_project_id FROM taskforge.project WHERE institution = '1' AND code = 'termx';
  IF v_project_id IS NULL THEN
    RAISE NOTICE 'taskforge: no default project found, skipping workflow creation';
    RETURN;
  END IF;

  INSERT INTO taskforge.workflow (project_id, task_type, transitions)
  SELECT v_project_id, v.task_type, v.transitions
  FROM (VALUES
    ('valueset-review', jsonb_build_array(
      '{"from": null, "to": "requested"}'::jsonb,
      '{"from": "requested", "to": "accepted"}'::jsonb,
      '{"from": "requested", "to": "rejected"}'::jsonb,
      '{"from": "requested", "to": "cancelled"}'::jsonb,
      '{"from": "accepted", "to": "cancelled"}'::jsonb,
      '{"from": "rejected", "to": "cancelled"}'::jsonb
    )),
    ('conceptmap-review', jsonb_build_array(
      '{"from": null, "to": "requested"}'::jsonb,
      '{"from": "requested", "to": "accepted"}'::jsonb,
      '{"from": "requested", "to": "rejected"}'::jsonb,
      '{"from": "requested", "to": "cancelled"}'::jsonb,
      '{"from": "accepted", "to": "cancelled"}'::jsonb,
      '{"from": "rejected", "to": "cancelled"}'::jsonb
    )),
    ('wiki-task', jsonb_build_array(
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
  ) AS v(task_type, transitions)
  WHERE NOT EXISTS (
    SELECT 1 FROM taskforge.workflow w
    WHERE w.task_type = v.task_type AND w.project_id = v_project_id
  );
END;
$$;
--
