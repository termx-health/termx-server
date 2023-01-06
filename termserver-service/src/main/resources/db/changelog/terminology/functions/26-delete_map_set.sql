drop function if exists terminology.delete_map_set(p_map_set text);

create or replace function terminology.delete_map_set(p_map_set text)
    returns void
    language sql
as
$function$

delete from terminology.entity_version_map_set_version_membership
where map_set_version_id in (select id from terminology.map_set_version where map_set = p_map_set) or
    map_set_entity_version_id in (select id from terminology.map_set_entity_version where map_set_entity_id in (select id from terminology.map_set_entity where map_set = p_map_set));

delete from terminology.map_set_entity_version
where map_set_entity_id in (select id from terminology.map_set_entity where map_set = p_map_set);

delete from terminology.map_set_association
where map_set = p_map_set or id in (select id from terminology.map_set_entity where map_set = p_map_set);

delete from terminology.map_set_entity
where map_set = p_map_set;

delete from terminology.map_set_version
where map_set = p_map_set;

delete from terminology.map_set
where id = p_map_set;
$function$
;
