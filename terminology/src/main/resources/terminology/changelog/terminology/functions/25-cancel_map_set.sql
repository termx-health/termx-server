drop function if exists terminology.cancel_map_set(p_map_set text);

create or replace function terminology.cancel_map_set(p_map_set text)
    returns void
    language sql
as
$function$

update terminology.entity_version_map_set_version_membership
set sys_status = 'D'
where map_set_version_id in (select id from terminology.map_set_version where map_set = p_map_set) or
      map_set_entity_version_id in (select id from terminology.map_set_entity_version where map_set_entity_id in (select id from terminology.map_set_entity where map_set = p_map_set));

update terminology.map_set_entity_version
set sys_status = 'D'
where map_set_entity_id in (select id from terminology.map_set_entity where map_set = p_map_set);

update terminology.map_set_association
set sys_status = 'D'
where map_set = p_map_set or id in (select id from terminology.map_set_entity where map_set = p_map_set);

update terminology.map_set_entity
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
