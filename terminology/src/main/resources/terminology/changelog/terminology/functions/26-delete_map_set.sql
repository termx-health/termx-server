drop function if exists terminology.delete_map_set(p_map_set text);

create or replace function terminology.delete_map_set(p_map_set text)
    returns void
    language sql
as
$function$
delete from terminology.map_set_statistics
where map_set = p_map_set;

delete from terminology.map_set_association
where map_set = p_map_set;

delete from terminology.map_set_version
where map_set = p_map_set;

delete from terminology.map_set
where id = p_map_set;
$function$
;
