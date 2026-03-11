CREATE OR REPLACE FUNCTION core.object_notify() RETURNS trigger AS $$
BEGIN
  PERFORM pg_notify('object_notify', TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME || '#' || TG_OP);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql VOLATILE;


CREATE OR REPLACE FUNCTION core.add_object_notifier(target_table regclass) RETURNS void AS $$
BEGIN
    EXECUTE 'DROP TRIGGER IF EXISTS object_notify ON ' || target_table;
    EXECUTE 'CREATE TRIGGER object_notify AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON '
          || target_table
          || ' FOR EACH STATEMENT EXECUTE PROCEDURE core.object_notify();';
END;
$$ language 'plpgsql';
