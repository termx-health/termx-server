create or replace function core.create_table_metadata(p_table_name varchar) returns varchar language plpgsql volatile as
$BODY$
declare
  rc bigint;
  l_table_name text;
  l_table text;
  sql text;
begin
  l_table := lower(p_table_name);
  l_table_name := reverse(split_part(reverse(l_table), '.', 1)); -- without schema, if present.

  -- create trigger corresponding table trigger type
  sql := 'DROP TRIGGER IF EXISTS thi_' || l_table_name ||' ON ' || l_table;
  perform core.exec(sql);
  sql := 'CREATE TRIGGER thi_' || l_table_name ||' BEFORE INSERT OR UPDATE ON ' || l_table ||' FOR EACH ROW EXECUTE PROCEDURE core.sys_columns()';
  perform core.exec(sql);
  
  return '(Y)';
end;
$BODY$;
