drop function if exists terminology.cancel_map_set_version(p_map_set_version_id bigint);

create or replace function terminology.cancel_map_set_version(p_map_set_version_id bigint)
    returns void
    language sql
as
$function$

update terminology.map_set_statistics
set sys_status = 'D'
where map_set_version_id = p_map_set_version_id;

update terminology.map_set_association
set sys_status = 'D'
where map_set_version_id = p_map_set_version_id;

update terminology.map_set_version
set sys_status = 'D'
where id = p_map_set_version_id;
$function$
;
