drop function if exists terminology.delete_value_set(p_value_set text);

create or replace function terminology.delete_value_set(p_value_set text)
    returns void
    language sql
as
$function$

delete from terminology.value_set_version_concept
where value_set_version_id in (select id from terminology.value_set_version where value_set = p_value_set);

delete from terminology.value_set_version_rule
where value_set = p_value_set or
    value_set_version_id in (select id from terminology.value_set_version where value_set = p_value_set) or
    rule_set_id in (select id from terminology.value_set_version_rule_set where value_set_version_id in (select id from terminology.value_set_version where value_set = p_value_set));

delete from terminology.value_set_version_rule_set
where value_set_version_id in (select id from terminology.value_set_version where value_set = p_value_set);

select terminology.delete_map_set(id) from terminology.map_set where source_value_set = p_value_set or target_value_set = p_value_set;

delete from terminology.value_set_version
where value_set = p_value_set;

delete from terminology.value_set
where id = p_value_set;
$function$
;
