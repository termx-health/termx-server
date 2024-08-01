drop function if exists terminology.change_code_system_id(p_current_code_system text, p_new_code_system text);

create or replace function terminology.change_code_system_id(p_current_code_system text, p_new_code_system text)
    returns void
    language sql
as
$function$
update terminology.code_system set id = p_new_code_system where id = p_current_code_system;

update terminology.entity_property
    set rule = jsonb_set(rule, '{codeSystems}', (replace(rule ->> 'codeSystems', p_current_code_system, p_new_code_system))::jsonb)
    where (rule ->> 'codeSystems') like '%' || p_current_code_system || '%';

update terminology.entity_property_value
set value = jsonb_set("value", '{codeSystem}', ('"' || p_new_code_system || '"')::jsonb)
where "value" ->> 'codeSystem' = p_current_code_system;

update sys.provenance set target = jsonb_set(target, '{id}', ('"' || p_new_code_system || '"')::jsonb)
  where target ->> 'type' = 'CodeSystem' and target ->> 'id' = p_current_code_system;
update sys.provenance set context = replace(context::text, '"' || p_current_code_system || '"', '"' || p_new_code_system || '"')::jsonb
  where context @? ('$[*] ? (@.entity.type == "CodeSystem" && @.entity.id == "' || p_current_code_system || '")')::jsonpath;


$function$
;
