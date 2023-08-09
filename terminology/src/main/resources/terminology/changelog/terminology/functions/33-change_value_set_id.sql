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

update sys.provenance set target = jsonb_set(target, '{id}', ('"' || p_new_value_set || '"')::jsonb)
  where target ->> 'type' = 'ValueSet' and target ->> 'id' = p_current_value_set;
update sys.provenance set context = replace(context::text, '"' || p_current_value_set || '"', '"' || p_new_value_set || '"')::jsonb
  where context @? ('$[*] ? (@.entity.type == "ValueSet" && @.entity.id == "' || p_current_value_set || '")')::jsonpath;


$function$
;
