drop function if exists terminology.cancel_map_set(p_map_set text);

create or replace function terminology.cancel_map_set(p_map_set text)
    returns void
    language sql
as
$function$
update terminology.map_set_statistics
set sys_status = 'D'
where map_set = p_map_set;

update terminology.map_set_association
set sys_status = 'D'
where map_set = p_map_set;

update terminology.map_set_version
set sys_status = 'D'
where map_set = p_map_set;

update terminology.map_set
set sys_status = 'D'
where id = p_map_set;
$function$
;
