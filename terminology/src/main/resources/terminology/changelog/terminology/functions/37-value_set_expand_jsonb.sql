DROP FUNCTION if exists terminology.value_set_expand(p_valueset text);

CREATE OR REPLACE FUNCTION terminology.value_set_expand(p_valueset text)
 RETURNS TABLE(concept jsonb, display jsonb, additional_designations jsonb, order_number integer, enumerated boolean)
 LANGUAGE sql
AS $function$
/* example call
select * from terminology.value_set_expand('
{
  "resourceType": "ValueSet",
  "language": "et",
  "compose": {
    "inactive": false,
    "include": [
      {
        "system": "https://www.medicinosnk.lt/CodeSystem/lt-lab-klt-nomenclature",
        "version": "1.0.6",
        "filter": [
          {
            "property": "method",
            "op": "=",
            "value": "{\"code\":\"CHROM\",\"codeSystem\":\"lt-lab-methods\"}"
          }
        ]
      },
      {
        "system": "https://www.medicinosnk.lt/CodeSystem/lt-lab-klt-nomenclature",
        "version": "1.0.6",
        "filter": [
          {
            "property": "parent",
            "op": "=",
            "value": "XLT00001-1"
          }
        ]
      },
      {
        "system": "https://www.medicinosnk.lt/CodeSystem/lt-lab-klt-nomenclature",
        "version": "1.0.6",
        "filter": [
          {
            "property": "code",
            "op": "in",
            "value": "XLT00040,XLT00041-7"
          }
        ]
      },
      {
        "system": "https://www.medicinosnk.lt/CodeSystem/lt-lab-klt-nomenclature",
        "version": "1.0.6",
        "filter": [
          {
            "property": "long-name",
            "op": "regex",
            "value": "Imunoglobulinų lengvosios, bakterijos izoliato"
          }
        ]
      },
      {
        "system" : "https://www.medicinosnk.lt/CodeSystem/lt-lab-klt-nomenclature",
        "concept" : [
          { "code" : "10608-8", "display" : "unknown", "designation": [{"language":"et", "value":"tundmatu"}], "property":[{"code":"order","valueInteger":1 }]},
          { "code" : "101674-0", "property":[{"code": "order","valueInteger": 2 }]}
        ]
      },
      {
        "system" : "http://hl7.org/fhir/administrative-gender"
      }
    ]
  }
}
');
*/
WITH recursive vs AS (
  SELECT p_valueset::jsonb resource
), rules AS (
    -- Extracts the rule level (include/exclude) and the associated system
    SELECT
        'include' as type,
        inc->>'system' as system,
        inc->>'version' as version,
        inc->>'filter' as filters,
        row_number() over () as rule_id,
        jsonb_array_length((inc->>'filter')::jsonb) as fcnt
    FROM vs, jsonb_array_elements(resource->'compose'->'include') AS inc
    WHERE inc->'concept' IS NULL OR jsonb_array_length(inc->'concept') = 0
    UNION ALL
    SELECT
        'exclude',
        exc->>'system',
        exc->>'version',
        exc->>'filter',
        (row_number() over ())*-1,
        jsonb_array_length((exc->>'filter')::jsonb)
    FROM vs, jsonb_array_elements(resource->'compose'->'exclude') AS exc
    WHERE exc->'concept' IS NULL OR jsonb_array_length(exc->'concept') = 0
)
, concepts AS (
    -- Flattens the nested concepts within each include/exclude block
    SELECT
        'include' as type,
        inc->>'system' as system,
        inc->>'version' as version,
        con->>'code' as code,
        con->>'display' as display,
        con->'designation' as designation,
        con->'property' as property
    FROM vs,
         jsonb_array_elements(resource->'compose'->'include') AS inc,
         jsonb_array_elements(inc->'concept') AS con
    UNION ALL
    SELECT
        'exclude',
        exc->>'system',
        exc->>'version',
        con->>'code' ,
        con->>'display',
        con->'designation',
        con->'property'
    FROM vs,
         jsonb_array_elements(resource->'compose'->'exclude') AS exc,
         jsonb_array_elements(exc->'concept') AS con
)
, exact_concepts as (
  -- all concepts included directly
  select t.type, cs.id "codeSystem", t.code, csev.id "conceptVersionId", t.display,
         t.designation, t.property,
         jsonb_path_query_first( t.property, '$[*] ? (@.code == "order").valueInteger')::integer AS order_nr,
         jsonb_build_object('conceptVersionId', csev.id, 'code', c.code, 'codeSystem', cs.id, 'codeSystemUri', cs.uri, 'baseCodeSystemUri', bcs.uri) obj
    from concepts t
         inner join terminology.code_system cs on cs.uri = t."system" and cs.sys_status = 'A'
         left outer join terminology.code_system bcs on bcs.id = cs.base_code_system and bcs.sys_status = 'A'
         inner join terminology.code_system_version csv on csv.code_system = cs.id and csv.sys_status = 'A'
                and ((t.version is not null and csv."version" = t.version ) or
                     (t.version is null and csv.id = (select max(id) from terminology.code_system_version t2 where t2.code_system = cs.id and t2.sys_status='A'))
                    )
         inner join terminology.concept c on csv.code_system  = c.code_system and c.code = t.code and c.sys_status = 'A'
         inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A'
         inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
                and evcsvm.code_system_entity_version_id = csev.id and csv.id = evcsvm.code_system_version_id
)
, t as (
  -- Find the proper code_system_version for every rule
  select r.type, cs.id "codeSystem", cs.uri "codeSystemUri", bcs.uri as "baseCodeSystemUri", csv.version, csv.id code_system_version_id,
         -- Use jsonb_set to replace the string "value": "{\"code\":\"CHROM\" with its jsonb equivalent
         jsonb_set(f.elem, '{value}',
         -- If it's already a JSON object string (starts with {), cast it
         case
         -- If it's already a JSON object string (starts with {), cast it
            when f.elem->>'value' LIKE '{%' THEN (f.elem->>'value')::jsonb
            -- else it's a simple string, convert it to a JSONB string safely
            else to_jsonb(f.elem->>'value')
        end
         ) filter_,
         r.rule_id, r.fcnt, row_number() over (partition by r.rule_id) rn
    from rules r
         -- spit filters array to the list and calculete the number of the filters
         cross join lateral jsonb_array_elements(r.filters::jsonb) as f(elem)
         inner join terminology.code_system cs on cs.uri = r."system" and cs.sys_status = 'A'
         left outer join terminology.code_system bcs on bcs.id = cs.base_code_system and bcs.sys_status = 'A'
         inner join terminology.code_system_version csv on csv.code_system = cs.id and csv.sys_status = 'A'
                and ((r.version is not null and csv."version" = r.version ) or
                     (r.version is null and csv.id = (select max(id) from terminology.code_system_version t2
                                                       where t2.code_system = cs.id and t2.sys_status='A'))
                    )
   where r.filters is not null
)
--select * from rules where filters is null or filters::jsonb=('[]'::jsonb) -- (filter_ ->> 'property') = 'display'
--
, r as (
  -- list of the all recursive values expressed with is-a operator
  select csev.code_system "codeSystem", csa.source_code_system_entity_version_id csev_id, csev2.code, t.rn, t.fcnt, t.rule_id, t.type, t.code_system_version_id
    from t
         inner join terminology.code_system_entity_version csev
                 on csev.code_system = t."codeSystem" and csev.sys_status = 'A'
                and csev.code = (t.filter_ ->> 'value')::text
         inner join terminology.code_system_association csa
                 on csa.association_type in ('is-a','part-of','child-of') and csa.sys_status = 'A'
                and csev.id = csa.target_code_system_entity_version_id
         left outer join terminology.entity_version_code_system_version_membership evcsvm
                 on evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = t.code_system_version_id
                and csev.id = evcsvm.code_system_entity_version_id
         inner join terminology.code_system_entity_version csev2 on csev2.id = csa.source_code_system_entity_version_id and csev2.sys_status = 'A'
   where t.filter_ ->> 'op' in ('is-a','child-of','descendent-leaf') or (t.filter_ ->> 'op' = '=' and t.filter_ ->> 'property' = 'parent')
   union
  select r."codeSystem", csa1.source_code_system_entity_version_id csev_id, csev2.code, rn, fcnt, rule_id, type, code_system_version_id
    from r, terminology.code_system_association csa1
         inner join terminology.code_system_entity_version csev2 on csev2.id = csa1.source_code_system_entity_version_id and csev2.sys_status = 'A'
   where r."codeSystem"=csa1.code_system
     and r.csev_id = csa1.target_code_system_entity_version_id
     and csa1.sys_status = 'A'
)
, c as (
  select c.code, t.*, csev.id csev_id
    from t
         inner join terminology.concept c on c.code_system = t."codeSystem" and c.sys_status = 'A'
         inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A'
         inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
                and evcsvm.code_system_entity_version_id = csev.id and evcsvm.code_system_version_id = t.code_system_version_id
   where t.filter_ is not null and (
         -- processing of the custom properties
         ((t.filter_ ->> 'op') not in ('is-a','part-of','child-of','descendent-leaf') and
          (t.filter_ ->> 'property') not in ('code', 'concept') and
          exists (select 1
                    from terminology.entity_property_value epv, terminology.entity_property ep,
                        unnest(string_to_array(t.filter_ ->> 'value', ',')) AS pattern1,
                        unnest(string_to_array(t.filter_ -> 'value' ->> 'code', ',')) AS pattern2
                  where epv.sys_status = 'A' and csev.id = epv.code_system_entity_version_id
                    and ep.sys_status = 'A' and ep.code_system = c.code_system and ep.id = epv.entity_property_id
                    and (t.filter_ ->> 'property') = ep.name
                    -- include support for '=', 'in', 'regex' operators
                    and (
                         ep.type <> 'Coding' and ((t.filter_ ->> 'value') is null or (t.filter_ -> 'value') = epv.value::jsonb or
                         (epv.value ->> 'code')::text ~ trim(pattern1))
                         or
                         ep.type = 'Coding' and  ((t.filter_ -> 'value' ->> 'code') is null or (t.filter_ -> 'value' -> 'code') = epv.value::jsonb or
                         (epv.value ->> 'code')::text ~ trim(pattern2))
                    )
                 )
          ) or
          -- processing of the code and cocept columns with '=', 'in', 'regex' operators
          ((t.filter_ ->> 'property') in ('code', 'concept') and
           (c.code = (t.filter_ ->> 'value')::text or
            exists(select 1 from unnest(string_to_array(t.filter_ ->> 'value', ',')) AS pattern
                    where c.code ~ trim(pattern) )
          ))
  )
)
, d as (
  select c.code, t.*, csev.id csev_id
    from t
         inner join terminology.concept c on c.code_system = t."codeSystem" and c.sys_status = 'A'
         inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A'
         inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
                and evcsvm.code_system_entity_version_id = csev.id and evcsvm.code_system_version_id = t.code_system_version_id
         cross join lateral unnest(string_to_array((t.filter_ ->> 'value')::text, ',')) AS pattern
         inner join terminology.entity_property ep on ep.sys_status = 'A' and (t.filter_ ->> 'property')::text = ep.name
         inner join terminology.designation d on d.sys_status = 'A' and ep.id = d.designation_type_id and d.code_system_entity_version_id = csev.id
   where t.filter_ is not null
     and d.name ~* (
         -- Converts 'by plasma$, serum' -> '(by plasma$|serum)'
         '(' || replace(pattern, ',', '|') || ')')
)
, all_findings as (
  select z.rule_id, z."type", t.fcnt, z."codeSystem", z.code_system_version_id, z.csev_id, z.code,
         jsonb_build_object('conceptVersionId', z.csev_id, 'code', z.code, 'codeSystem', z."codeSystem", 'codeSystemUri', t."codeSystemUri", 'baseCodeSystemUri', t."baseCodeSystemUri") obj
    from r as z
         inner join t on z.rule_id=t.rule_id and z.rn=t.rn
  union
  select z.rule_id, z."type", z.fcnt, z."codeSystem", z.code_system_version_id, z.csev_id, z.code,
         jsonb_build_object('conceptVersionId', z.csev_id, 'code', z.code, 'codeSystem', z."codeSystem", 'codeSystemUri', z."codeSystemUri", 'baseCodeSystemUri', z."baseCodeSystemUri")
    from c as z
  union
  select z.rule_id, z."type", z.fcnt, z."codeSystem", z.code_system_version_id, z.csev_id, z.code,
         jsonb_build_object('conceptVersionId', z.csev_id, 'code', z.code, 'codeSystem', z."codeSystem", 'codeSystemUri', z."codeSystemUri", 'baseCodeSystemUri', z."baseCodeSystemUri")
    from d as z
)
, expression_concepts as (
  select rule_id, type, code, obj, fcnt, count(*)
    from all_findings
   group by rule_id, type, code, obj, fcnt
  having fcnt = count(*)
)
, cs as (
  select r.type, csev.code,
         jsonb_build_object('conceptVersionId', csev.id, 'code', csev.code, 'codeSystem', cs.id, 'codeSystemUri', cs.uri, 'baseCodeSystemUri', bcs.uri) obj
    from rules r
         inner join terminology.code_system cs on cs.uri = r."system" and cs.sys_status = 'A'
         left outer join terminology.code_system bcs on bcs.id = cs.base_code_system and bcs.sys_status = 'A'
         inner join terminology.code_system_version csv on csv.code_system = cs.id and csv.sys_status = 'A'
                and ((r.version is not null and csv."version" = r.version ) or
                     (r.version is null and csv.id = (select max(id) from terminology.code_system_version t2
                                                       where t2.code_system = cs.id and t2.sys_status='A'))
                    )
         inner join terminology.code_system_entity_version csev on cs.id = csev.code_system and csev.sys_status='A'
         inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
                and evcsvm.code_system_version_id = csv.id and csev.id = evcsvm.code_system_entity_version_id
   where r.filters is null or r.filters::jsonb=('[]'::jsonb)
),
codes as (
  select code, obj
    from cs
   where type='include'
/*  union
  select concept ->> 'code', concept
    from vs*/
  union
   select code, obj
    from expression_concepts
   where type='include'
  union
  select code, obj
    from exact_concepts
    where type='include'
  except
  select code, obj
    from cs
   where type='exclude'
  except
  select code, obj
    from expression_concepts
   where type='exclude'
  except
  select code, obj
    from exact_concepts
    where type='exclude'
)
select t.obj, to_jsonb(ec.display), ec.designation::jsonb, ec.order_nr,
       case when ec.code is not null and ec.display is not null then true else false end
  from codes t left outer join exact_concepts ec on t.code = ec.code
 order by ec.order_nr;

$function$
;
