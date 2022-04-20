CREATE OR REPLACE FUNCTION core.sys_columns()
  RETURNS trigger AS
$BODY$
DECLARE
  l_ts          TIMESTAMP := CURRENT_TIMESTAMP;
  l_env         TEXT := coalesce(core.get_setting('core.env'),'prod');
  sys_column    text;
BEGIN
  IF core.get_setting('core.sys_columns_disabled') = 'true' THEN
    return new;
  end if;

  IF TG_OP = 'UPDATE' and new = old then
    return new;
  end if;

  FOR sys_column IN
    SELECT column_name::text
       FROM information_schema.columns
       WHERE table_name = TG_TABLE_NAME
       AND column_name like 'sys%'
  LOOP

    IF sys_column = 'sys_version' THEN
        new.sys_version = CASE WHEN TG_OP = 'INSERT' THEN coalesce(new.sys_version,0)+1
                               WHEN TG_OP = 'UPDATE' THEN coalesce(new.sys_version,old.sys_version,0)+1 END;
    ELSEIF sys_column = 'sys_modified_at' THEN
      new.sys_modified_at = l_ts;
    ELSEIF sys_column = 'sys_modified_by' THEN
      if l_env='dev' then
        new.sys_modified_by = coalesce(core.session_user(), 'env-' || l_env);
      else
        new.sys_modified_by = core.session_user();
      end if;
    ELSEIF TG_OP = 'INSERT' and sys_column = 'sys_status' THEN
      if new.sys_status is null then
        new.sys_status = 'A';
      end if;
    ELSEIF TG_OP = 'INSERT' and sys_column = 'sys_created_at' THEN
      new.sys_created_at = l_ts;
    ELSEIF TG_OP = 'INSERT' and sys_column = 'sys_created_by' THEN
      if l_env='dev' then
        new.sys_created_by = coalesce(core.session_user(), 'env-' || l_env);
      else
        new.sys_created_by = core.session_user();
      end if;
    END IF;
  END LOOP;

  RETURN NEW;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE
  COST 10;
