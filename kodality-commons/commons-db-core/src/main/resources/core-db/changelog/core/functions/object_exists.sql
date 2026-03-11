create or replace function core.object_exists 
(
  p_object_name varchar,
  p_object_type varchar default null,
  p_schema_name varchar default null 
) returns smallint 
language plpgsql volatile
as 
$body$
declare
  l_cnt smallint;
  l_type char;
begin
  SELECT CASE lower(p_object_type)
              WHEN 'ordinary table' THEN 'r'  
              WHEN 'table' THEN 'r'  
              WHEN 'index' THEN 'i'  
              WHEN 'sequence' THEN 'S' 
              WHEN 'view' THEN 'v'  
              WHEN 'materialized view' THEN 'm'  
              WHEN 'composite type' THEN 'c'
              WHEN 'toast table' THEN 't'  
              WHEN 'foreign table' THEN 'f' 
              ELSE NULL END
         INTO l_type;

  SELECT case when EXISTS (
    SELECT 1 
      FROM pg_catalog.pg_class c
           JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = lower(coalesce(p_schema_name,n.nspname))
       AND c.relname = lower(p_object_name)
       AND c.relkind = coalesce(l_type,c.relkind::char)
  ) then 1 else 0 end into l_cnt;
 
  return l_cnt;
end;
$body$
