drop function if exists terminology.cancel_value_set_version(p_value_set_version_id bigint);

create or replace function terminology.cancel_value_set_version(p_value_set_version_id bigint)
    returns void
    language sql
as
$function$

update terminology.value_set_version_concept
set sys_status = 'D'
where value_set_version_id = p_value_set_version_id;

update terminology.value_set_version_rule
set sys_status = 'D'
where value_set_version_id = p_value_set_version_id or
    rule_set_id in (select id from terminology.value_set_version_rule_set where value_set_version_id  = p_value_set_version_id);

update terminology.value_set_version_rule_set
set sys_status = 'D'
where value_set_version_id = p_value_set_version_id;

update terminology.value_set_version
set sys_status = 'D'
where id = p_value_set_version_id;
$function$
;
