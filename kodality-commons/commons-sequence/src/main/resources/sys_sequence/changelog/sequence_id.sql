create or replace function core.sequence_id(in_code in text, in_scope in text default null, in_tenant in text default null) returns bigint LANGUAGE sql stable as
$BODY$
  select id
  from core.sys_sequence
  where code = in_code
  and coalesce(in_scope,'0') = coalesce(scope,'0')
  and (tenant is null or coalesce(in_tenant, '0') = tenant)
  order by (case when tenant is null then 1 else 0 end)
  limit 1;
$BODY$
