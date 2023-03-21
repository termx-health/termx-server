drop function if exists terminology.cancel_map_set_version(p_map_set_version_id bigint);

create or replace function terminology.cancel_map_set_version(p_map_set_version_id bigint)
    returns void
    language sql
as
$function$

update terminology.entity_version_map_set_version_membership
set sys_status = 'D'
where map_set_version_id = p_map_set_version_id;

update terminology.map_set_entity_version
set sys_status = 'D'
where id in (select map_set_entity_version_id from terminology.entity_version_map_set_version_membership where map_set_version_id = p_map_set_version_id);

update terminology.map_set_version
set sys_status = 'D'
where id = p_map_set_version_id;
$function$
;
