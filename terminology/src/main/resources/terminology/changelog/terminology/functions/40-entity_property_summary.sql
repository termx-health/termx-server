drop function if exists terminology.entity_property_summary(p_code_system text, p_code_system_version text);

create or replace function terminology.entity_property_summary(p_code_system text, p_code_system_version text)
    returns table (
        property_id             bigint,
        property_name           text,
        concept_cnt             int,
        prop_cnt                int,
        prop_list               text
    )
    language sql
as
$function$

select ep.id, ep.name, count(distinct csev.id) concept_cnt,
       count(distinct epv.value) prop_cnt, '[' || string_agg(distinct epv.value::text, ',') || ']' prop_list
  from terminology.code_system_version csv
       inner join terminology.code_system_entity cse ON csv.code_system = cse.code_system and cse.sys_status = 'A'
       inner join terminology.code_system_entity_version csev
               on csev.code_system = csv.code_system and csev.code_system_entity_id = cse.id and csev.sys_status = 'A'
       inner join terminology.entity_version_code_system_version_membership mem
               on csev.id = mem.code_system_entity_version_id and csv.id = mem.code_system_version_id and mem.sys_status = 'A'
       inner join terminology.entity_property_value epv ON csev.id =  epv.code_system_entity_version_id and epv.sys_status = 'A'
       inner join terminology.entity_property ep ON ep.id = epv.entity_property_id and ep.type= 'Coding' and ep.sys_status = 'A'
 where csv.code_system = p_code_system
   and (csv.version = p_code_system_version or p_code_system_version is null)
   and csv.sys_status = 'A'
 group by ep.id, ep.name;

$function$
;
