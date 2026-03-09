--liquibase formatted sql

--changeset termx:snapshot-1
alter table terminology.code_system_entity_version disable trigger all;

with t as (
  select id, status, csev.sys_created_at  
    from terminology.code_system_entity_version csev 
   where csev.sys_status='A' and csev.snapshot is null 
)
, d as (
  select d.code_system_entity_version_id,
         jsonb_agg(jsonb_build_object('language',d.language,'use',ep.name,'name',d.name) order by d.language, ep.name, d.name) val         
    from t 
         inner join terminology.designation d on d.code_system_entity_version_id = t.id
         inner join terminology.entity_property ep on ep.id = d.designation_type_id and ep.sys_status='A'
   where d.sys_status = 'A' 
   group by d.code_system_entity_version_id 
)
, pv as (
  select cs.code_system, term.* from (
    select epv.value ->> 'codeSystem' code_system, string_agg(distinct epv.value ->> 'code', ', ') AS codes
      from t 
           inner join terminology.entity_property_value epv on epv.code_system_entity_version_id = t.id
     where epv.sys_status = 'A' 
       and epv.value ->> 'codeSystem' is not null and epv.value ->> 'code' is not null
     group by epv.value ->> 'codeSystem'
  ) cs cross join lateral terminology.concept_info(p_code_system => cs.code_system, p_codes => cs.codes) as term
)
, p as (
  select epv.code_system_entity_version_id, 
         jsonb_agg(jsonb_build_object(
            'valueCoding', jsonb_build_object(
                'code', epv.value->> 'code',
                'system', epv.value->>'codeSystem',
                'version', pv.version,
                'display', pv.display),
            'concept_version_id',pv.concept_version_id,
            'concept_version',pv.concept_version
          )) val
    from t 
         inner join terminology.entity_property_value epv on epv.code_system_entity_version_id = t.id
         left outer join pv on (epv.value ->> 'codeSystem')=pv.code_system and (epv.value ->> 'code')=pv.code
   where epv.sys_status = 'A' 
  group by epv.code_system_entity_version_id
)
update terminology.code_system_entity_version csev 
   set snapshot = jsonb_strip_nulls(
                    jsonb_build_object(
                      'designation', d.val,
                      'properties', p.val
                    )
                  )
  from t
       left outer join d on t.id = d.code_system_entity_version_id
       left outer join p on t.id = p.code_system_entity_version_id
 where csev.id = t.id       
;   

alter table terminology.code_system_entity_version enable trigger all;

--rollback select 1;
