--liquibase formatted sql

-- =============================================================================
-- Smart migration: taskflow -> taskforge (project and workflow only)
--
-- If the old taskflow schema exists: copies project and workflow data,
-- preserving original IDs. ACLs in core.acl already reference the same
-- project IDs, so they carry over automatically.
--
-- If taskflow does not exist: creates default project (with ACL) and
-- workflow records so the system works on a fresh install.
--
-- In both cases, advances core.seq_id past any migrated IDs.
-- The data script (00-taskforge-data.sql) runs after this and fills in
-- any missing workflow types via WHERE NOT EXISTS.
-- =============================================================================

--changeset termx:smart-migrate-project-workflow splitStatements:false runOnChange:false
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'taskflow') THEN
    -- ── Migrate projects from taskflow (preserving IDs) ──
    INSERT INTO taskforge.project (id, institution, code, names, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by)
    SELECT id, institution, code, names, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by
    FROM taskflow.project
    ON CONFLICT DO NOTHING;

    -- ── Migrate workflows from taskflow (preserving IDs) ──
    INSERT INTO taskforge.workflow (id, project_id, task_type, transitions, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by)
    SELECT id, project_id, task_type, transitions, sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by
    FROM taskflow.workflow
    ON CONFLICT DO NOTHING;

    RAISE NOTICE 'taskforge: migrated % projects and % workflows from taskflow',
      (SELECT count(*) FROM taskforge.project),
      (SELECT count(*) FROM taskforge.workflow);

  ELSE
    -- ── No taskflow schema — create default project ──
    IF NOT EXISTS (SELECT 1 FROM taskforge.project WHERE institution = '1' AND code = 'termx') THEN
      INSERT INTO taskforge.project (institution, code, names)
      VALUES ('1', 'termx', '{"en": "TermX"}'::jsonb);

      INSERT INTO core.acl (s_id, tenant, access)
      SELECT p.id, null, 'consume'
      FROM taskforge.project p
      WHERE p.institution = '1' AND p.code = 'termx'
        AND NOT core.aclchk(p.id, null, 'consume');
    END IF;

    -- ── Create default workflows for the project ──
    WITH proj AS (
      SELECT id FROM taskforge.project WHERE institution = '1' AND code = 'termx'
    ),
    vals(task_type, transitions) AS (VALUES
      ('task', jsonb_build_array(
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
      )),
      ('version-review', jsonb_build_array(
        '{"from": null, "to": "requested"}'::jsonb,
        '{"from": "requested", "to": "accepted"}'::jsonb,
        '{"from": "requested", "to": "rejected"}'::jsonb,
        '{"from": "requested", "to": "cancelled"}'::jsonb,
        '{"from": "accepted", "to": "cancelled"}'::jsonb,
        '{"from": "rejected", "to": "cancelled"}'::jsonb
      )),
      ('version-approval', jsonb_build_array(
        '{"from": null, "to": "requested"}'::jsonb,
        '{"from": "requested", "to": "accepted"}'::jsonb,
        '{"from": "requested", "to": "rejected"}'::jsonb,
        '{"from": "requested", "to": "cancelled"}'::jsonb,
        '{"from": "accepted", "to": "cancelled"}'::jsonb,
        '{"from": "rejected", "to": "cancelled"}'::jsonb
      )),
      ('concept-review', jsonb_build_array(
        '{"from": null, "to": "requested"}'::jsonb,
        '{"from": "requested", "to": "accepted"}'::jsonb,
        '{"from": "requested", "to": "rejected"}'::jsonb,
        '{"from": "requested", "to": "cancelled"}'::jsonb,
        '{"from": "accepted", "to": "cancelled"}'::jsonb,
        '{"from": "rejected", "to": "cancelled"}'::jsonb
      )),
      ('concept-approval', jsonb_build_array(
        '{"from": null, "to": "requested"}'::jsonb,
        '{"from": "requested", "to": "accepted"}'::jsonb,
        '{"from": "requested", "to": "rejected"}'::jsonb,
        '{"from": "requested", "to": "cancelled"}'::jsonb,
        '{"from": "accepted", "to": "cancelled"}'::jsonb,
        '{"from": "rejected", "to": "cancelled"}'::jsonb
      )),
      ('wiki-page-comment', jsonb_build_array(
        '{"from": null, "to": "requested"}'::jsonb,
        '{"from": "requested", "to": "cancelled"}'::jsonb,
        '{"from": "requested", "to": "accepted"}'::jsonb,
        '{"from": "requested", "to": "completed"}'::jsonb,
        '{"from": "accepted", "to": "completed"}'::jsonb,
        '{"from": "accepted", "to": "cancelled"}'::jsonb
      ))
    )
    INSERT INTO taskforge.workflow (project_id, task_type, transitions)
    SELECT proj.id, vals.task_type, vals.transitions
    FROM proj, vals
    WHERE NOT EXISTS (
      SELECT 1 FROM taskforge.workflow w WHERE w.task_type = vals.task_type AND w.project_id = proj.id
    );

    RAISE NOTICE 'taskforge: created default project and % workflows (no taskflow schema found)',
      (SELECT count(*) FROM taskforge.workflow);
  END IF;

  -- ── Advance core.seq_id past any migrated project/workflow IDs ──
  PERFORM setval('core.seq_id', GREATEST(
    (SELECT coalesce(max(id), 0) FROM taskforge.project),
    (SELECT coalesce(max(id), 0) FROM taskforge.workflow),
    (SELECT last_value FROM core.seq_id)
  ));
END;
$$;
--
