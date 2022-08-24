drop function if exists terminology.code_system_closure(p_code_system_id text);

create or replace function terminology.code_system_closure(
    p_code_system_id text
)
    returns text[]
    language sql stable
as
$function$
with recursive rec as (
    select cs.id, cs.base_code_system from terminology.code_system cs where cs.sys_status = 'A' and cs.id = p_code_system_id
    union all
    select bcs.id, bcs.base_code_system from terminology.code_system bcs join rec cs on cs.base_code_system = bcs.id and bcs.sys_status = 'A'
)
select array_agg(rec.id) from rec;
$function$
;
