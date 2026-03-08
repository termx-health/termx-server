CREATE OR REPLACE FUNCTION taskforge.task_action_trigger() RETURNS TRIGGER AS $body$
DECLARE
  diff hstore;
  transition jsonb;
BEGIN
  diff = (hstore(NEW.*) - hstore(OLD.*)) - array['id', 'sys_status', 'sys_version', 'sys_created_at', 'sys_created_by', 'sys_modified_at', 'sys_modified_by'];
  IF diff = hstore('') THEN
    RETURN NULL;
  END IF;

  select jsonb_object_agg(x.k, jsonb_build_object('from', hstore(OLD.*) -> x.k, 'to', hstore(NEW.*) -> x.k))
  into transition
  from unnest(akeys(diff)) as x(k);

  INSERT INTO taskforge.task_activity (task_id, transition, updated_by, updated_at)
    select NEW.id, transition, NEW.updated_by, NEW.updated_at;
  RETURN NULL;
END;
$body$
LANGUAGE plpgsql;
