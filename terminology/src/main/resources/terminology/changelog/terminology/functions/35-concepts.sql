drop function if exists terminology.concepts(text, text, text, text, text, text)
;

CREATE OR REPLACE FUNCTION terminology.concepts(p_code_system text, p_version text default null,
  p_association_type text default null, p_ids text default null, p_codes text default null, p_text text default null)
 RETURNS TABLE(code_system text, version text, concept_id bigint, code text, concept_version_id bigint, level int, leaf boolean,
  parent_id bigint, parent_code text, status text, path text, display jsonb)
 LANGUAGE sql
AS $function$
with recursive st as (
  SELECT terminology.search_translate(trim(unnest(string_to_array(p_text, ' ')))) AS value
), r as (
  select c.code_system, csv.version, c.id concept_id, c.code, csev.id concept_version_id, 1 level,
         true leaf, null::bigint parent_id, null::text parent_code, csev.status, c.code as path
    from terminology.concept c
         inner join terminology.code_system_version csv on csv.code_system  = c.code_system and csv.sys_status = 'A'
         inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A'
         inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
                and evcsvm.code_system_entity_version_id = csev.id and csv.id = evcsvm.code_system_version_id
  where c.sys_status = 'A'
    and p_association_type is not null
    and c.code_system = p_code_system
    and csv.version = coalesce(p_version, csv.version)
    and (p_ids is null or (p_ids is not null and c.id in (select trim(unnest(string_to_array(p_ids, ',')))::bigint )))
    and (p_codes is null or (p_codes is not null and c.code like ANY (select trim(unnest(string_to_array(p_codes, ',')))||'%' )))
    and (p_text is null or (p_text is not null and exists(
        select 1 from terminology.designation d
         where d.code_system_entity_version_id = csev.id and d.sys_status = 'A'
           and terminology.text_search(d.name) like (SELECT '%'||string_agg(value,'%')||'%' from st)
    )))
    and ((p_codes is not null or p_ids is not null or p_text is not null) or (p_codes is null and p_ids is null and p_text is null
        and exists(select 1 from terminology.code_system_association csa
                    where csa.target_code_system_entity_version_id = csev.id and csa.sys_status = 'A'
                      and csa.association_type = p_association_type )
        and not exists(select 1 from terminology.code_system_association csa
                        where csa.source_code_system_entity_version_id = csev.id and csa.sys_status = 'A'
                          and csa.association_type = p_association_type )
        ))
  union all
  select c.code_system, csv.version, c.id concept_id, c.code, csev.id concept_version_id, r.level + 1,
         not exists(select 1
                      from terminology.code_system_association csa
                           inner join terminology.code_system_entity_version csev on csev.id = csa.target_code_system_entity_version_id and csev.sys_status = 'A'
                    where csa.sys_status = 'A' and csev.code_system_entity_id = c.id and csa.association_type = p_association_type),
         r.concept_id, r.code, csev.status, r.path ||'.'||c.code
    from r
        inner join terminology.code_system_association csa on csa.code_system=r.code_system and
              csa.target_code_system_entity_version_id = r.concept_version_id and csa.sys_status = 'A' --and csa.association_type = 'is-a'
        inner join terminology.code_system_entity_version csev on csa.source_code_system_entity_version_id = csev.id and csev.sys_status = 'A'
        inner join terminology.concept c on csev.code_system_entity_id = c.id and c.sys_status = 'A'
        left outer join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_entity_version_id = csev.id  and evcsvm.sys_status = 'A'
        left outer join terminology.code_system_version csv on csv.id = evcsvm.code_system_version_id and csv.sys_status = 'A'
),
t as (
  -- list of the concepts with associations
  select * from r
  union
  -- list of the concepts without any association
  select c.code_system, csv.version, c.id concept_id, c.code, csev.id concept_version_id, 1 level, true leaf,
         null::bigint parent_id, null::text parent_code, csev.status, c.code path
    from terminology.concept c
        inner join terminology.code_system_version csv on csv.code_system  = c.code_system and csv.sys_status = 'A'
        inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A'
        left join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
             and evcsvm.code_system_entity_version_id = csev.id and csv.id = evcsvm.code_system_version_id
  where c.sys_status = 'A'
    and c.code_system = p_code_system
    and csv.version = coalesce(p_version, csv.version)
    and (p_ids is null or (p_ids is not null and c.id in (select trim(unnest(string_to_array(p_ids, ',')))::bigint )))
    and (p_codes is null or (p_codes is not null and c.code like ANY (select trim(unnest(string_to_array(p_codes, ',')))||'%' )))
    and (p_text is null or (p_text is not null and exists(
        select 1 from terminology.designation d
         where d.code_system_entity_version_id = csev.id and d.sys_status = 'A'
           and terminology.text_search(d.name) like (SELECT '%'||string_agg(value,'%')||'%' from st)
    )))
    and (p_association_type is null or (p_association_type is not null and (p_ids is not null or p_codes is not null or p_text is not null)
        and not exists(select 1 from terminology.code_system_association csa
                    where csa.target_code_system_entity_version_id = csev.id and csa.sys_status = 'A' and csa.association_type = p_association_type )
        and not exists(select 1 from terminology.code_system_association csa
                    where csa.source_code_system_entity_version_id = csev.id and csa.sys_status = 'A' and csa.association_type = p_association_type )
    ))
)
select t.*,
       jsonb_agg(jsonb_build_object('language',d.language,'name',d.name,'type',ep.name))
  from t left outer join terminology.designation d on
              d.code_system_entity_version_id = t.concept_version_id and d.sys_status = 'A'
         left outer join terminology.entity_property ep on ep.id = d.designation_type_id
group by t.code_system, t.version, t.concept_id, t.code, t.concept_version_id, t.level, t.leaf,
         t.parent_id, t.parent_code, t.status, t.path
order by t.path;

$function$
;
