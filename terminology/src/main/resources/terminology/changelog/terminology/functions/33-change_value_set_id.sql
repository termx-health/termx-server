drop function if exists terminology.change_value_set_id(p_current_value_set text, p_new_value_set text);

create or replace function terminology.change_value_set_id(p_current_value_set text, p_new_value_set text)
    returns void
    language sql
as
$function$
update terminology.value_set set id = p_new_value_set where id = p_current_value_set;

update terminology.entity_property
    set rule = jsonb_set(rule, '{valueSet}', to_jsonb(p_new_value_set))
    where (rule ->> 'valueSet') like '%' || p_current_value_set || '%';

$function$
;
