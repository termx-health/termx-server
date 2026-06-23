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
         jsonb_build_object('conceptVersionId', csev.id, 'code', t.code, 'codeSystem', cs.id, 'codeSystemUri', cs.uri, 'baseCodeSystemUri', bcs.uri) obj
    from concepts t
         inner join terminology.code_system cs on cs.uri = t."system" and cs.sys_status = 'A'
         left outer join terminology.code_system bcs on bcs.id = cs.base_code_system and bcs.sys_status = 'A'
         inner join terminology.code_system_version csv on csv.code_system = cs.id and csv.sys_status = 'A'
                and ((t.version is not null and csv."version" = t.version ) or
                     (t.version is null and csv.id = (select t2.id
                                                        from terminology.code_system_version t2
                                                       where t2.code_system = cs.id
                                                         and t2.sys_status = 'A'
                                                         and t2.status in ('active', 'draft')
                                                       order by coalesce(t2.release_date, now()) desc, t2.version desc, t2.id desc
                                                       limit 1))
                    )
         left outer join terminology.code_system_entity_version csev on csev.code_system = cs.id
                and csev.code = t.code
                and csev.sys_status = 'A'
         left outer join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
                and evcsvm.code_system_entity_version_id = csev.id and csv.id = evcsvm.code_system_version_id
   where (evcsvm.id is not null or cs.content = 'not-present')
     and not exists (
         select 1
           from terminology.entity_version_code_system_version_membership evcsvm_excluded
          where evcsvm_excluded.code_system_version_id = csv.id
            and evcsvm_excluded.code_system_entity_version_id = csev.id
            and evcsvm_excluded.sys_status = 'C'
     )
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
                     (r.version is null and csv.id = (select t2.id
                                                        from terminology.code_system_version t2
                                                       where t2.code_system = cs.id
                                                         and t2.sys_status = 'A'
                                                         and t2.status in ('active', 'draft')
                                                       order by coalesce(t2.release_date, now()) desc, t2.version desc, t2.id desc
                                                       limit 1))
                    )
   where r.filters is not null
)
--select * from rules where filters is null or filters::jsonb=('[]'::jsonb) -- (filter_ ->> 'property') = 'display'
--
, r as (
  -- Forward hierarchy: direct children (level 1) of the filter value, then recurse downward.
  -- Drives is-a / descendent-of / child-of / descendent-leaf / is-not-a, plus the legacy
  -- `parent =` shorthand (kept as "all descendants, no self" for backwards compatibility).
  select t."codeSystem", csa.source_code_system_entity_version_id csev_id, child.code,
         t.rn, t.fcnt, t.rule_id, t.type, t.code_system_version_id, t."codeSystemUri", t."baseCodeSystemUri",
         (t.filter_ ->> 'op') operator, anchor.id parent, 1 as level
    from t
         inner join terminology.code_system_entity_version anchor
                 on anchor.code_system = t."codeSystem" and anchor.sys_status = 'A'
                and anchor.code = (t.filter_ ->> 'value')::text
         inner join terminology.code_system_association csa
                 on csa.association_type in ('is-a','part-of','child-of') and csa.sys_status = 'A'
                and anchor.id = csa.target_code_system_entity_version_id
         inner join terminology.code_system_entity_version child
                 on child.id = csa.source_code_system_entity_version_id and child.sys_status = 'A'
   where (t.filter_ ->> 'op') in ('is-a','descendent-of','child-of','descendent-leaf','is-not-a')
      or ((t.filter_ ->> 'op') = '=' and (t.filter_ ->> 'property') = 'parent')
   union
  select r."codeSystem", csa1.source_code_system_entity_version_id csev_id, child.code,
         r.rn, r.fcnt, r.rule_id, r.type, r.code_system_version_id, r."codeSystemUri", r."baseCodeSystemUri",
         r.operator, r.csev_id parent, r.level + 1
    from r
         inner join terminology.code_system_association csa1
                 on csa1.association_type in ('is-a','part-of','child-of') and csa1.sys_status = 'A'
                and r.csev_id = csa1.target_code_system_entity_version_id
         inner join terminology.code_system_entity_version child
                 on child.id = csa1.source_code_system_entity_version_id and child.sys_status = 'A'
   where r."codeSystem" = csa1.code_system
)
, rr as (
  -- Reverse hierarchy: ancestors of the filter value, for the generalizes operator.
  select t."codeSystem", csa.target_code_system_entity_version_id csev_id, parent.code,
         t.rn, t.fcnt, t.rule_id, t.type, t.code_system_version_id, t."codeSystemUri", t."baseCodeSystemUri"
    from t
         inner join terminology.code_system_entity_version anchor
                 on anchor.code_system = t."codeSystem" and anchor.sys_status = 'A'
                and anchor.code = (t.filter_ ->> 'value')::text
         inner join terminology.code_system_association csa
                 on csa.association_type in ('is-a','part-of','child-of') and csa.sys_status = 'A'
                and anchor.id = csa.source_code_system_entity_version_id
         inner join terminology.code_system_entity_version parent
                 on parent.id = csa.target_code_system_entity_version_id and parent.sys_status = 'A'
   where (t.filter_ ->> 'op') = 'generalizes'
   union
  select rr."codeSystem", csa1.target_code_system_entity_version_id csev_id, parent.code,
         rr.rn, rr.fcnt, rr.rule_id, rr.type, rr.code_system_version_id, rr."codeSystemUri", rr."baseCodeSystemUri"
    from rr
         inner join terminology.code_system_association csa1
                 on csa1.association_type in ('is-a','part-of','child-of') and csa1.sys_status = 'A'
                and rr.csev_id = csa1.source_code_system_entity_version_id
         inner join terminology.code_system_entity_version parent
                 on parent.id = csa1.target_code_system_entity_version_id and parent.sys_status = 'A'
   where rr."codeSystem" = csa1.code_system
)
, rc as (
  -- The filter value concept itself ("self"), included for is-a / generalizes; carried for is-not-a
  -- so it can be subtracted from the complement.
  select t."codeSystem", anchor.id csev_id, anchor.code,
         t.rn, t.fcnt, t.rule_id, t.type, t.code_system_version_id, t."codeSystemUri", t."baseCodeSystemUri",
         (t.filter_ ->> 'op') operator
    from t
         inner join terminology.code_system_entity_version anchor
                 on anchor.code_system = t."codeSystem" and anchor.sys_status = 'A'
                and anchor.code = (t.filter_ ->> 'value')::text
   where (t.filter_ ->> 'op') in ('is-a','generalizes','is-not-a')
)
, pool as (
  -- Every concept in the rule's code system version; the complement source for is-not-a.
  select t."codeSystem", csev.id csev_id, csev.code,
         t.rn, t.fcnt, t.rule_id, t.type, t.code_system_version_id, t."codeSystemUri", t."baseCodeSystemUri"
    from t
         inner join terminology.code_system_entity_version csev
                 on csev.code_system = t."codeSystem" and csev.sys_status = 'A'
         inner join terminology.entity_version_code_system_version_membership evcsvm
                 on evcsvm.sys_status = 'A' and evcsvm.code_system_entity_version_id = csev.id
                and evcsvm.code_system_version_id = t.code_system_version_id
   where (t.filter_ ->> 'op') = 'is-not-a'
)
, c as (
  select csev.id csev_id, con.code,
         t.rn, t.fcnt, t.rule_id, t.type, t.code_system_version_id, t."codeSystem", t."codeSystemUri", t."baseCodeSystemUri"
    from t
         inner join terminology.concept con on con.code_system = t."codeSystem" and con.sys_status = 'A'
         inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = con.id and csev.sys_status = 'A'
         inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
                and evcsvm.code_system_entity_version_id = csev.id and evcsvm.code_system_version_id = t.code_system_version_id
   where t.filter_ is not null and (
         -- code / concept column with '=', 'in', 'regex', 'not-in'
         ((t.filter_ ->> 'property') in ('code', 'concept') and (t.filter_ ->> 'op') in ('=','in','regex','not-in') and (
              (t.filter_ ->> 'op') = '='      and con.code = (t.filter_ ->> 'value')::text or
              -- FHIR filter regex matches the ENTIRE code: anchor both ends as `^(value)$`. The previous
              -- `code = any(regexp_match(code, value||'$'))` form only anchored the tail and compared the
              -- code against a CAPTURED GROUP, so a value carrying its own group (e.g. `(a+)+`) returned the
              -- inner capture instead of the whole match and matched nothing. The wrapping parens keep
              -- top-level alternation (`a|b`) bound to the anchors.
              (t.filter_ ->> 'op') = 'regex'  and con.code ~ ('^(' || (t.filter_ ->> 'value')::text || ')$') or
              (t.filter_ ->> 'op') = 'in'     and con.code = any(string_to_array((t.filter_ ->> 'value')::text, ',')) or
              (t.filter_ ->> 'op') = 'not-in' and con.code != all(string_to_array((t.filter_ ->> 'value')::text, ','))
         ))
         or
         -- custom property with '=', 'in', 'regex'. A scalar (string/number) value is compared via
         -- `epv.value #>> '{}'` (the UNQUOTED text form — `::text` would keep the jsonb quotes and never
         -- match a plain string); a Coding value via `epv.value ->> 'code'`. The filter value may itself
         -- be a scalar or a Coding object ({"code":…}), so both forms are accepted. No cross-joined
         -- unnest (which previously produced zero rows, hence no match, for scalar filter values).
         ((t.filter_ ->> 'property') not in ('code', 'concept') and (t.filter_ ->> 'op') in ('=','in','regex') and
          exists (select 1
                    from terminology.entity_property_value epv, terminology.entity_property ep
                  where epv.sys_status = 'A' and csev.id = epv.code_system_entity_version_id
                    and ep.sys_status = 'A' and ep.code_system = con.code_system and ep.id = epv.entity_property_id
                    and (t.filter_ ->> 'property') = ep.name
                    and ((t.filter_ ->> 'value') is null
                         or (t.filter_ ->> 'op') = '=' and (
                              epv.value::jsonb = (t.filter_ -> 'value')
                              or (epv.value #>> '{}') = (t.filter_ ->> 'value')
                              or (epv.value ->> 'code') = (t.filter_ ->> 'value')
                              or (epv.value ->> 'code') = (t.filter_ -> 'value' ->> 'code'))
                         or (t.filter_ ->> 'op') = 'in' and (
                              (epv.value #>> '{}') = any(string_to_array((t.filter_ ->> 'value'), ','))
                              or (epv.value ->> 'code') = any(string_to_array((t.filter_ ->> 'value'), ','))
                              or (epv.value ->> 'code') = any(string_to_array((t.filter_ -> 'value' ->> 'code'), ',')))
                         or (t.filter_ ->> 'op') = 'regex' and (
                              (epv.value #>> '{}') ~ (t.filter_ ->> 'value')
                              or (epv.value ->> 'code') ~ (t.filter_ ->> 'value')))
                 ))
         or
         -- custom property with 'exists' (value true => has >=1 value; value false => has none)
         ((t.filter_ ->> 'property') not in ('code', 'concept') and (t.filter_ ->> 'op') = 'exists' and (
              (t.filter_ ->> 'value')::text = 'true' and exists (
                  select 1 from terminology.entity_property_value epv, terminology.entity_property ep
                   where epv.sys_status = 'A' and csev.id = epv.code_system_entity_version_id
                     and ep.sys_status = 'A' and ep.code_system = con.code_system and ep.id = epv.entity_property_id
                     and (t.filter_ ->> 'property') = ep.name)
              or
              (t.filter_ ->> 'value')::text = 'false' and not exists (
                  select 1 from terminology.entity_property_value epv, terminology.entity_property ep
                   where epv.sys_status = 'A' and csev.id = epv.code_system_entity_version_id
                     and ep.sys_status = 'A' and ep.code_system = con.code_system and ep.id = epv.entity_property_id
                     and (t.filter_ ->> 'property') = ep.name)
         ))
         or
         -- custom property with 'not-in' (no value of the property is in the set)
         ((t.filter_ ->> 'property') not in ('code', 'concept') and (t.filter_ ->> 'op') = 'not-in' and
          not exists (select 1
                        from terminology.entity_property_value epv, terminology.entity_property ep
                       where epv.sys_status = 'A' and csev.id = epv.code_system_entity_version_id
                         and ep.sys_status = 'A' and ep.code_system = con.code_system and ep.id = epv.entity_property_id
                         and (t.filter_ ->> 'property') = ep.name
                         and ((epv.value #>> '{}') = any(string_to_array((t.filter_ ->> 'value'), ','))
                              or (epv.value ->> 'code') = any(string_to_array((t.filter_ ->> 'value'), ',')))))
  )
)
, d as (
  select csev.id csev_id, con.code,
         t.rn, t.fcnt, t.rule_id, t.type, t.code_system_version_id, t."codeSystem", t."codeSystemUri", t."baseCodeSystemUri"
    from t
         inner join terminology.concept con on con.code_system = t."codeSystem" and con.sys_status = 'A'
         inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = con.id and csev.sys_status = 'A'
         inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.sys_status = 'A'
                and evcsvm.code_system_entity_version_id = csev.id and evcsvm.code_system_version_id = t.code_system_version_id
         cross join lateral unnest(string_to_array((t.filter_ ->> 'value')::text, ',')) AS pattern
         inner join terminology.entity_property ep on ep.sys_status = 'A' and (t.filter_ ->> 'property')::text = ep.name
         inner join terminology.designation dn on dn.sys_status = 'A' and ep.id = dn.designation_type_id and dn.code_system_entity_version_id = csev.id
   where t.filter_ is not null
     and (t.filter_ ->> 'property') not in ('code', 'concept')
     and (t.filter_ ->> 'op') in ('=','in','regex')
     and dn.name ~* (
         -- Converts 'by plasma$, serum' -> '(by plasma$|serum)'
         '(' || replace(pattern, ',', '|') || ')')
)
, hier as (
  -- Forward-hierarchy descendants, shaped per operator: child-of (and the legacy `parent =`
  -- shorthand) keep only level 1 — direct children; descendent-leaf keeps only descendants that have
  -- no child of their own; is-not-a is handled by the complement below (excluded here). Membership in
  -- the rule's version is enforced. NB: operator '=' reaches `r` only via `parent =`.
  select z.rn, z.rule_id, z.type, z.fcnt, z."codeSystem", z.code_system_version_id, z.csev_id, z.code, z."codeSystemUri", z."baseCodeSystemUri"
    from r z
   where z.operator <> 'is-not-a'
     and (z.operator not in ('child-of', '=') or z.level = 1)
     and (z.operator <> 'descendent-leaf' or not exists (
            select 1 from r r1 where r1.rule_id = z.rule_id and r1."codeSystem" = z."codeSystem" and r1.parent = z.csev_id))
     and exists (select 1 from terminology.entity_version_code_system_version_membership m
                  where m.sys_status = 'A' and m.code_system_entity_version_id = z.csev_id and m.code_system_version_id = z.code_system_version_id)
  union
  -- generalizes ancestors
  select rr.rn, rr.rule_id, rr.type, rr.fcnt, rr."codeSystem", rr.code_system_version_id, rr.csev_id, rr.code, rr."codeSystemUri", rr."baseCodeSystemUri"
    from rr
   where exists (select 1 from terminology.entity_version_code_system_version_membership m
                  where m.sys_status = 'A' and m.code_system_entity_version_id = rr.csev_id and m.code_system_version_id = rr.code_system_version_id)
  union
  -- the filter value concept itself for is-a / generalizes
  select rc.rn, rc.rule_id, rc.type, rc.fcnt, rc."codeSystem", rc.code_system_version_id, rc.csev_id, rc.code, rc."codeSystemUri", rc."baseCodeSystemUri"
    from rc
   where rc.operator in ('is-a','generalizes')
     and exists (select 1 from terminology.entity_version_code_system_version_membership m
                  where m.sys_status = 'A' and m.code_system_entity_version_id = rc.csev_id and m.code_system_version_id = rc.code_system_version_id)
  union
  -- is-not-a: every member of the version that is neither in the forward set nor the concept itself
  select p.rn, p.rule_id, p.type, p.fcnt, p."codeSystem", p.code_system_version_id, p.csev_id, p.code, p."codeSystemUri", p."baseCodeSystemUri"
    from pool p
   where not exists (select 1 from r r2 where r2.rule_id = p.rule_id and r2."codeSystem" = p."codeSystem" and r2.csev_id = p.csev_id and r2.operator = 'is-not-a')
     and not exists (select 1 from rc rc2 where rc2.rule_id = p.rule_id and rc2."codeSystem" = p."codeSystem" and rc2.csev_id = p.csev_id)
)
, all_findings as (
  select z.rn, z.rule_id, z."type", z.fcnt, z."codeSystem", z.code_system_version_id, z.csev_id, z.code,
         jsonb_build_object('conceptVersionId', z.csev_id, 'code', z.code, 'codeSystem', z."codeSystem", 'codeSystemUri', z."codeSystemUri", 'baseCodeSystemUri', z."baseCodeSystemUri") obj
    from hier as z
  union
  select z.rn, z.rule_id, z."type", z.fcnt, z."codeSystem", z.code_system_version_id, z.csev_id, z.code,
         jsonb_build_object('conceptVersionId', z.csev_id, 'code', z.code, 'codeSystem', z."codeSystem", 'codeSystemUri', z."codeSystemUri", 'baseCodeSystemUri', z."baseCodeSystemUri")
    from c as z
  union
  select z.rn, z.rule_id, z."type", z.fcnt, z."codeSystem", z.code_system_version_id, z.csev_id, z.code,
         jsonb_build_object('conceptVersionId', z.csev_id, 'code', z.code, 'codeSystem', z."codeSystem", 'codeSystemUri', z."codeSystemUri", 'baseCodeSystemUri', z."baseCodeSystemUri")
    from d as z
)
, expression_concepts as (
  -- A rule's filters combine with logical AND (FHIR compose.include[].filter[]): a concept is kept
  -- only when it matched every filter of the rule. Each finding carries the rn of the filter that
  -- produced it, so a concept that satisfied all `fcnt` filters has `fcnt` distinct rn. (#196)
  select rule_id, type, code, obj, fcnt
    from all_findings
   group by rule_id, type, code, obj, fcnt
  having count(distinct rn) = fcnt
)
, cs as (
  select r.type, csev.code,
         jsonb_build_object('conceptVersionId', csev.id, 'code', csev.code, 'codeSystem', cs.id, 'codeSystemUri', cs.uri, 'baseCodeSystemUri', bcs.uri) obj
    from rules r
         inner join terminology.code_system cs on cs.uri = r."system" and cs.sys_status = 'A'
         left outer join terminology.code_system bcs on bcs.id = cs.base_code_system and bcs.sys_status = 'A'
         inner join terminology.code_system_version csv on csv.code_system = cs.id and csv.sys_status = 'A'
                and ((r.version is not null and csv."version" = r.version ) or
                     (r.version is null and csv.id = (select t2.id
                                                        from terminology.code_system_version t2
                                                       where t2.code_system = cs.id
                                                         and t2.sys_status = 'A'
                                                         and t2.status in ('active', 'draft')
                                                       order by coalesce(t2.release_date, now()) desc, t2.version desc, t2.id desc
                                                       limit 1))
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
       -- A code is "enumerated" when it comes from an explicit compose.include.concept entry, regardless of whether
       -- that entry carried a display (the display is resolved later) — mirrors 01-value_set_expand. Without this an
       -- enumerated value set that lists bare codes is mis-flagged non-enumerated and gets wrongly hierarchy-nested.
       case when ec.code is not null then true else false end
  from codes t left outer join exact_concepts ec on t.code = ec.code
 order by ec.order_nr;

$function$
;
