CREATE OR REPLACE FUNCTION terminology.concept_info(p_code_system text, p_version text DEFAULT NULL::text, p_codes text DEFAULT NULL::text, p_time timestamp DEFAULT null::timestamp)
 RETURNS TABLE(version text, concept_id bigint, code text, concept_version_id bigint, concept_status text, concept_version timestamp, display text)
 LANGUAGE sql
 STABLE              -- Crucial for reducing calls in JOINs
 PARALLEL SAFE       -- Allows multi-core execution
AS $function$

with target_version as (
    select id, code_system, version
    from terminology.code_system_version
    where code_system = p_code_system
      and sys_status = 'A'
      and sys_created_at <= coalesce(p_time, now())
      and (p_version is null or version = p_version)
    order by sys_created_at desc, id desc
    limit 1
),
target_codes as (
    select trim(unnest(string_to_array(p_codes, ','))) || '%' as pattern
    where p_codes is not null
), t as (
select csv.version, c.id, c.code, csev.id concept_version_id, csev.status concept_status, evcsvm.sys_created_at concept_version
  from target_version csv
       inner join terminology.concept c on csv.code_system  = c.code_system and c.sys_status = 'A'
       inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A'
       inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
             and evcsvm.code_system_entity_version_id = csev.id and csv.id = evcsvm.code_system_version_id
 where (exists( SELECT 1 FROM target_codes tc WHERE c.code LIKE tc.pattern))
)
select t.version, t.id, t.code, t.concept_version_id, t.concept_status, t.concept_version,
       jsonb_agg(jsonb_build_object('language',d.language,'name',d.name)
                 order by d.language, d.name
       )
  from t
       inner join terminology.designation d on
              d.code_system_entity_version_id = t.concept_version_id and d.sys_status = 'A'
       inner join terminology.entity_property ep on ep.id = d.designation_type_id and ep.name = 'display'
 group by t.version, t.id, t.code, t.concept_version_id, t.concept_status, t.concept_version;

$function$
;
