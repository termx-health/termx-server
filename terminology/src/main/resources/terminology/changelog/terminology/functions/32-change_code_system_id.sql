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

$function$
;