drop function if exists sys.change_implementation_guide_id(p_current_ig text, p_new_ig text);

create or replace function sys.change_implementation_guide_id(p_current_ig text, p_new_ig text)
    returns void
    language sql
as
$function$
update sys.implementation_guide set id = p_new_ig where id = p_current_ig;

update sys.provenance set target = jsonb_set(target, '{id}', ('"' || p_new_ig || '"')::jsonb)
  where target ->> 'type' = 'ImplementationGuide' and target ->> 'id' = p_current_ig;
update sys.provenance set context = replace(context::text, '"' || p_current_ig || '"', '"' || p_new_ig || '"')::jsonb
  where context @? ('$[*] ? (@.entity.type == "ImplementationGuide" && @.entity.id == "' || p_current_ig || '")')::jsonpath;


$function$
;
