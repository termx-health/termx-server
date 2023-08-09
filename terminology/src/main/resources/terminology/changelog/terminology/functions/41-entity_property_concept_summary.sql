drop function if exists terminology.entity_property_concept_summary(p_code_system text, p_code_system_version text, p_entity_property_id bigint);

create or replace function terminology.entity_property_concept_summary(p_code_system text, p_code_system_version text, p_entity_property_id bigint)
    returns table (
        property_code           text,
        concept_cnt             int,
        concept_ids             text
    )
    language sql
as
$function$

select epv.value ->> 'code', count(*) concept_cnt, '[' || string_agg(csev.id::text, ',') || ']' concept_ids
  from terminology.code_system_version csv
       inner join terminology.code_system_entity cse ON csv.code_system = cse.code_system and cse.sys_status = 'A'
       inner join terminology.code_system_entity_version csev
               on csev.code_system = csv.code_system and csev.code_system_entity_id = cse.id and csev.sys_status = 'A'
       inner join terminology.entity_version_code_system_version_membership mem
               on csev.id = mem.code_system_entity_version_id and csv.id = mem.code_system_version_id and mem.sys_status = 'A'
       inner join terminology.entity_property_value epv ON csev.id =  epv.code_system_entity_version_id and epv.sys_status = 'A'
       inner join terminology.entity_property epd ON epd.id = epv.entity_property_id and epd.type='Coding' and epd.sys_status = 'A'
 where csv.code_system = p_code_system
   and (csv.version = p_code_system_version or p_code_system_version is null)
   and epd.id = p_entity_property_id
   and csv.sys_status = 'A'
group by epv.value ->> 'code';

$function$
;




