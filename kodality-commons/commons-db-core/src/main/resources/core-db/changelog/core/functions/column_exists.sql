create or replace function core.column_exists 
(
  p_table_name varchar,
  p_column_name varchar,
  p_schema_name varchar default null 
) returns smallint 
language plpgsql volatile
as 
$body$
declare
  l_cnt smallint;
begin
  SELECT count(*) into l_cnt
    FROM information_schema.columns 
   WHERE table_name=p_table_name and column_name=p_column_name and table_schema=coalesce(p_schema_name,table_schema);
 
  return l_cnt;
end;
$body$
