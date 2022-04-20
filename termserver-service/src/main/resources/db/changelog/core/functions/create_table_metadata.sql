create or replace function core.create_table_metadata(p_table_name varchar) returns varchar language plpgsql volatile as
$BODY$
declare
  l_table text;
  sql text;
begin
  l_table := lower(p_table_name);

  -- create trigger corresponding table trigger type
  sql := 'DROP TRIGGER IF EXISTS thi_' || l_table ||' ON ' || l_table;
  perform exec(sql);
  sql := 'CREATE TRIGGER thi_' || l_table ||' BEFORE INSERT OR UPDATE ON ' || l_table ||' FOR EACH ROW EXECUTE PROCEDURE sys_columns()';
  perform exec(sql);

  return '(Y)';
end;
$BODY$;
