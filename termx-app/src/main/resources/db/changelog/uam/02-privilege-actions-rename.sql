--liquibase formatted sql

--changeset termx:rename_actions_view_edit_publish_to_read_write_maintain
-- Phase B migration: rename action keys in uam.privilege_resource.actions JSONB
-- from view/edit/publish to read/write/maintain. Idempotent: only rewrites rows
-- that still carry the legacy keys.
--
-- Uses jsonb_exists_any() instead of the `?|` operator to avoid JDBC parsing
-- the `?` as a positional parameter placeholder.
update uam.privilege_resource
set actions =
  (actions
    - 'view' - 'edit' - 'publish')
    || jsonb_strip_nulls(jsonb_build_object(
         'read',     actions->'view',
         'write',    actions->'edit',
         'maintain', actions->'publish'
       ))
where jsonb_exists_any(actions, array['view', 'edit', 'publish']);
--rollback update uam.privilege_resource
--rollback set actions =
--rollback   (actions
--rollback     - 'read' - 'write' - 'maintain')
--rollback     || jsonb_strip_nulls(jsonb_build_object(
--rollback          'view',    actions->'read',
--rollback          'edit',    actions->'write',
--rollback          'publish', actions->'maintain'
--rollback        ))
--rollback where jsonb_exists_any(actions, array['read', 'write', 'maintain']);
