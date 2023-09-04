drop function if exists terminology.change_map_set_id(p_current_map_set text, p_new_map_set text);

create or replace function terminology.change_map_set_id(p_current_map_set text, p_new_map_set text)
    returns void
    language sql
as
$function$
update terminology.map_set set id = p_new_map_set where id = p_current_map_set;

update sys.provenance set target = jsonb_set(target, '{id}', ('"' || p_new_map_set || '"')::jsonb)
  where target ->> 'type' = 'MapSet' and target ->> 'id' = p_current_map_set;
update sys.provenance set context = replace(context::text, '"' || p_current_map_set || '"', '"' || p_new_map_set || '"')::jsonb
  where context @? ('$[*] ? (@.entity.type == "MapSet" && @.entity.id == "' || p_current_map_set || '")')::jsonpath;


$function$
;
