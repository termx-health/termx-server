drop function if exists terminology.value_set_expand(p_value_set_version_id bigint);

create or replace function terminology.value_set_expand(
    p_value_set_version_id bigint
)
    returns table (
        concept                 jsonb,
        display                 jsonb,
        additional_designations jsonb,
        order_number            smallint,
        enumerated              boolean
    )
    language sql
as
$function$
with rules as (
  select r.id rule_id, r."type", r.code_system,
         r.code_system_version_id as original_code_system_version_id,
         coalesce(r.code_system_version_id,
             (select csv.id
                from terminology.code_system_version csv
               where csv.code_system = r.code_system
                 and csv.status = 'active'
                 and csv.sys_status = 'A'
               order by csv.release_date desc, csv.id desc
               limit 1)
         ) as code_system_version_id,
         r.filters, r.concepts, cs.uri uri, bcs.uri base_uri, r.value_set_version_id
    from terminology.value_set_version_rule_set rs
         inner join terminology.value_set_version v on v.id = rs.value_set_version_id and v.sys_status = 'A'
         inner join terminology.value_set_version_rule r on r.rule_set_id = rs.id and r.sys_status = 'A'
                and ((r.code_system is not null and r.code_system not in ('snomed-ct'/*,'loinc'*/,'ucum')) or r.value_set_version_id is not null)
         left outer join terminology.code_system cs on cs.sys_status = 'A' and cs.id = r.code_system
         left outer join terminology.code_system bcs on bcs.sys_status = 'A' and bcs.id = cs.base_code_system
   where v.id = p_value_set_version_id and rs.sys_status = 'A'
),
exact_concepts as (
  with t as (
    select r.*, jsonb_array_elements(r.concepts) concept
      from rules r
  )
  select t.rule_id, t."type", t.code_system, t.original_code_system_version_id, t.code_system_version_id,
         t.concept -> 'concept' ->> 'code' csev_code,
         jsonb_build_object('conceptVersionId', csev.id,
                            'code', t.concept -> 'concept' ->> 'code',
                            'codeSystem', t.code_system, 'codeSystemUri', t.uri, 'baseCodeSystemUri', t.base_uri, 'codeSystemVersionId', t.code_system_version_id) obj,
         (t.concept -> 'display') display,
         (t.concept -> 'additionalDesignations') additional_designations,
         (t.concept -> 'orderNumber')::smallint order_number
    from t
         inner join terminology.code_system cs on cs.id = t.code_system and cs.sys_status = 'A'
         left outer join terminology.code_system_entity_version csev on t.code_system = csev.code_system
                and (t.concept -> 'concept' ->> 'code') = csev.code
                and csev.sys_status='A'
         left outer join terminology.entity_version_code_system_version_membership evcsvm
                 on evcsvm.sys_status = 'A'
                and csev.id = evcsvm.code_system_entity_version_id
                and (evcsvm.code_system_version_id = t.code_system_version_id
                      or evcsvm.code_system_version_id in (
                          select csv2.id
                            from terminology.code_system_version csv2
                           where csv2.code_system = t.code_system
                             and csv2.sys_status = 'A'
                             and csv2.release_date <= (select csv3.release_date
                                                         from terminology.code_system_version csv3
                                                        where csv3.id = t.code_system_version_id)))
   where (evcsvm.id is not null or cs.content = 'not-present')
     and not exists (
         select 1
           from terminology.entity_version_code_system_version_membership evcsvm_excluded
          where evcsvm_excluded.code_system_version_id = t.code_system_version_id
            and evcsvm_excluded.code_system_entity_version_id = csev.id
            and evcsvm_excluded.sys_status = 'C'
     )
),
expressions as (
  -- list the all unique filters
  with recursive t as (
    with f as (
      select r.*, jsonb_array_elements(r.filters) filter_, jsonb_array_length(r.filters) fcnt
        from rules r
       where r.filters is not null
    ) select f.*,
             row_number() over () rn
        from f
  ),
  -- list of all recursive values expressed with is-a / descendent-of / child-of / is-not-a / descendent-leaf operators
  r_all as (
    select csev.code_system, csa.source_code_system_entity_version_id csev_id, t.rule_id, t.rn, t.fcnt, 1 as level, (t.filter_ ->> 'operator')::text operator, csev.id parent,
           evcsvm.code_system_version_id,
           row_number() over (partition by t.rule_id, t.code_system, csev.code, csa.source_code_system_entity_version_id
                              order by case when evcsvm.code_system_version_id = t.code_system_version_id then 0 else 1 end,
                                       csv.release_date desc) as version_rn
      from t
           inner join terminology.code_system_entity_version csev
                   on csev.code_system = t.code_system and csev.sys_status = 'A'
                  and csev.code = (t.filter_ ->> 'value')::text
           inner join terminology.code_system_association csa
                   on csev.id = csa.target_code_system_entity_version_id and csa.sys_status = 'A'
           inner join terminology.entity_version_code_system_version_membership evcsvm
                   on evcsvm.sys_status = 'A'
                  and csev.id = evcsvm.code_system_entity_version_id
                  and (evcsvm.code_system_version_id = t.code_system_version_id
                        or evcsvm.code_system_version_id in (
                            select csv2.id
                              from terminology.code_system_version csv2
                             where csv2.code_system = t.code_system
                               and csv2.sys_status = 'A'
                               and csv2.release_date <= (select csv3.release_date
                                                           from terminology.code_system_version csv3
                                                          where csv3.id = t.code_system_version_id)))
           inner join terminology.code_system_version csv
                   on csv.id = evcsvm.code_system_version_id and csv.sys_status = 'A'
     where (t.filter_ ->> 'operator')::text = any(array['is-a','descendent-of', 'child-of', 'is-not-a', 'descendent-leaf'])
       and not exists (
           select 1
             from terminology.entity_version_code_system_version_membership evcsvm_excluded
            where evcsvm_excluded.code_system_version_id = t.code_system_version_id
              and evcsvm_excluded.code_system_entity_version_id = csa.source_code_system_entity_version_id
              and evcsvm_excluded.sys_status = 'C'
       )
  ),
  r as (
    select code_system, csev_id, rule_id, rn, fcnt, level, operator, parent
      from r_all
     where r_all.version_rn = 1
    union
    select r.code_system, csa1.source_code_system_entity_version_id csev_id, rule_id, rn, fcnt, level + 1, operator, r.csev_id parent
      from r, terminology.code_system_association csa1
     where r.code_system=csa1.code_system
       and r.csev_id = csa1.target_code_system_entity_version_id
       and csa1.sys_status = 'A'
       and operator = any(array['is-a','descendent-of', 'child-of', 'is-not-a', 'descendent-leaf'])
  ),
  -- list of all recursive values expressed with generalizes operator (reverse direction)
  rr_all as (
    select csev.code_system, csa.target_code_system_entity_version_id csev_id, t.rule_id, t.rn, t.fcnt, 1 as level, (t.filter_ ->> 'operator')::text operator,
           evcsvm.code_system_version_id,
           row_number() over (partition by t.rule_id, t.code_system, csev.code, csa.target_code_system_entity_version_id
                              order by case when evcsvm.code_system_version_id = t.code_system_version_id then 0 else 1 end,
                                       csv.release_date desc) as version_rn
      from t
           inner join terminology.code_system_entity_version csev
                   on csev.code_system = t.code_system and csev.sys_status = 'A'
                  and csev.code = (t.filter_ ->> 'value')::text
           inner join terminology.code_system_association csa
                   on csev.id = csa.source_code_system_entity_version_id and csa.sys_status = 'A'
           inner join terminology.entity_version_code_system_version_membership evcsvm
                   on evcsvm.sys_status = 'A'
                  and csev.id = evcsvm.code_system_entity_version_id
                  and (evcsvm.code_system_version_id = t.code_system_version_id
                        or evcsvm.code_system_version_id in (
                            select csv2.id
                              from terminology.code_system_version csv2
                             where csv2.code_system = t.code_system
                               and csv2.sys_status = 'A'
                               and csv2.release_date <= (select csv3.release_date
                                                           from terminology.code_system_version csv3
                                                          where csv3.id = t.code_system_version_id)))
           inner join terminology.code_system_version csv
                   on csv.id = evcsvm.code_system_version_id and csv.sys_status = 'A'
     where (t.filter_ ->> 'operator')::text = 'generalizes'
       and not exists (
           select 1
             from terminology.entity_version_code_system_version_membership evcsvm_excluded
            where evcsvm_excluded.code_system_version_id = t.code_system_version_id
              and evcsvm_excluded.code_system_entity_version_id = csa.target_code_system_entity_version_id
              and evcsvm_excluded.sys_status = 'C'
       )
  ),
  rr as (
    select code_system, csev_id, rule_id, rn, fcnt, level, operator
      from rr_all
     where rr_all.version_rn = 1
    union
    select rr.code_system, csa1.target_code_system_entity_version_id csev_id, rule_id, rn, fcnt, level + 1, operator
      from rr, terminology.code_system_association csa1
     where rr.code_system=csa1.code_system
       and rr.csev_id = csa1.source_code_system_entity_version_id
       and csa1.sys_status = 'A'
       and operator = 'generalizes'
  ),
  -- the concept itself for is-a / generalizes / is-not-a (root concept inclusion/exclusion reference)
  rc as (
    select csev.code_system, csev.id csev_id, t.rule_id, t.rn, t.fcnt, (t.filter_ ->> 'operator') operator
      from t
           inner join terminology.code_system_entity_version csev
                   on csev.code_system = t.code_system and csev.sys_status = 'A'
                  and csev.code = (t.filter_ ->> 'value')::text
     where (t.filter_ ->> 'operator')::text = any(array['is-a', 'generalizes', 'is-not-a'])
  ),
  -- all concepts from code system versions (base pool for filter matching)
  c_all as (
    select t.rule_id, t."type", t.code_system, t.original_code_system_version_id, t.code_system_version_id, csev.id csev_id,
           csev.code csev_code,
           jsonb_build_object('conceptVersionId', csev.id,
                              'code', csev.code, 'codeSystem', t.code_system,
                              'codeSystemUri', t.uri, 'baseCodeSystemUri', t.base_uri, 'codeSystemVersionId', t.code_system_version_id) obj,
           row_number() over (partition by t.rule_id, t.code_system, csev.code
                              order by case when evcsvm.code_system_version_id = t.code_system_version_id then 0 else 1 end,
                                       csv.release_date desc) as version_rn
      from rules t
           inner join terminology.code_system_entity_version csev
                   on t.code_system = csev.code_system and csev.sys_status='A'
           inner join terminology.entity_version_code_system_version_membership evcsvm
                   on evcsvm.sys_status = 'A'
                  and csev.id = evcsvm.code_system_entity_version_id
                  and (evcsvm.code_system_version_id = t.code_system_version_id
                        or evcsvm.code_system_version_id in (
                            select csv2.id
                              from terminology.code_system_version csv2
                             where csv2.code_system = t.code_system
                               and csv2.sys_status = 'A'
                               and csv2.release_date <= (select csv3.release_date
                                                           from terminology.code_system_version csv3
                                                          where csv3.id = t.code_system_version_id)))
           inner join terminology.code_system_version csv
                   on csv.id = evcsvm.code_system_version_id and csv.sys_status = 'A'
     where t.filters is not null
       and not exists (
           select 1
             from terminology.entity_version_code_system_version_membership evcsvm_excluded
            where evcsvm_excluded.code_system_version_id = t.code_system_version_id
              and evcsvm_excluded.code_system_entity_version_id = csev.id
              and evcsvm_excluded.sys_status = 'C'
       )
  ),
  c as (
    select rule_id, "type", code_system, original_code_system_version_id, code_system_version_id, csev_id, csev_code, obj
      from c_all
     where version_rn = 1
  )
  -- concepts that match by exact code or regexp applied to code
  select c.*, t.rn, t.fcnt, 1 from c, t
   where t.code_system = c.code_system
     and t.rule_id = c.rule_id
     and (t.filter_ -> 'property' ->> 'name')::text = 'code' and (
         (t.filter_ ->> 'operator')::text = '=' and c.csev_code = (t.filter_ ->> 'value')::text or -- code equals the provided value
         (t.filter_ ->> 'operator')::text = 'regex' and c.csev_code = any(regexp_match(c.csev_code, (t.filter_ ->> 'value')::text||'$')) or -- code matches the regex
         (t.filter_ ->> 'operator')::text = 'in' and c.csev_code = any(string_to_array((t.filter_ ->> 'value')::text, ',')) or -- code is in the set
         (t.filter_ ->> 'operator')::text = 'not-in' and c.csev_code != all(string_to_array((t.filter_ ->> 'value')::text, ',')) -- code is not in the set
     )
  union
  -- concepts that match by properties
  select c.*, t.rn, t.fcnt, 1 from c, t
   where t.code_system = c.code_system
     and t.rule_id = c.rule_id
     and (t.filter_ -> 'property' ->> 'name')::text != all(array['code', 'concept'])
     and (exists (select 1 from terminology.entity_property_value epv, terminology.entity_property ep
                   where epv.sys_status = 'A' and c.csev_id = epv.code_system_entity_version_id
                     and ep.sys_status = 'A' and ep.id = epv.entity_property_id
                     and (t.filter_ -> 'property' ->> 'name')::text = ep.name
                     and ((t.filter_ ->> 'value') is null or
                          (t.filter_ ->> 'operator')::text = 'exists' and true = (t.filter_ -> 'value')::boolean or
                          (t.filter_ ->> 'operator')::text = '=' and (epv.value::jsonb = (t.filter_ -> 'value') or (epv.value ->> 'code')::text = (t.filter_ ->> 'value')::text) or
                          (t.filter_ ->> 'operator')::text = 'regex' and (epv.value::text = any(regexp_match(epv.value::text, (t.filter_ ->> 'value')::text||'$')) or (epv.value ->> 'code')::text = any(regexp_match((epv.value ->> 'code')::text, (t.filter_ ->> 'value')::text||'$'))) or
                          (t.filter_ ->> 'operator')::text = 'in' and (epv.value::text = any(string_to_array((t.filter_ ->> 'value')::text, ',')) or (epv.value ->> 'code')::text = any(string_to_array((t.filter_ ->> 'value')::text, ','))) or
                          (t.filter_ ->> 'operator')::text = 'not-in' and (epv.value::text != all(string_to_array((t.filter_ ->> 'value')::text, ',')) and (epv.value ->> 'code')::text != all(string_to_array((t.filter_ ->> 'value')::text, ',')))
                     ))
          or (t.filter_ ->> 'operator')::text = 'exists' and false = (t.filter_ -> 'value')::boolean and
             not exists (select 1 from terminology.entity_property_value epv, terminology.entity_property ep
                          where epv.sys_status = 'A' and c.csev_id = epv.code_system_entity_version_id
                            and ep.sys_status = 'A' and ep.id = epv.entity_property_id
                            and (t.filter_ -> 'property' ->> 'name')::text = ep.name)
     )
  union
  -- concepts that match by designations
  select c.*, t.rn, t.fcnt, 1 from c, t
   where t.code_system = c.code_system
     and t.rule_id = c.rule_id
     and (t.filter_ -> 'property' ->> 'name')::text != all(array['code', 'concept'])
     and exists (select 1 from terminology.designation d, terminology.entity_property ep
                  where d.sys_status = 'A' and c.csev_id = d.code_system_entity_version_id
                    and ep.sys_status = 'A' and ep.id = d.designation_type_id
                    and (t.filter_ -> 'property' ->> 'name')::text = ep.name
                    and ((t.filter_ ->> 'value') is null or
                         (t.filter_ ->> 'operator')::text = '=' and d.name = (t.filter_ ->> 'value')::text or
                         (t.filter_ ->> 'operator')::text = 'regex' and d.name = any(regexp_match(d.name, (t.filter_ ->> 'value')::text||'$')) or
                         (t.filter_ ->> 'operator')::text = 'in' and d.name = any(string_to_array((t.filter_ ->> 'value')::text, ',')) or
                         (t.filter_ ->> 'operator')::text = 'not-in' and d.name != all(string_to_array((t.filter_ ->> 'value')::text, ','))
                    ))
  union
  -- all recursive (hierarchical) concepts calculated before
  select c.*, r.rn, r.fcnt, r.level from c
  left join r  on c.code_system = r.code_system  and c.rule_id = r.rule_id  and c.csev_id = r.csev_id
  left join rc on c.code_system = rc.code_system and c.rule_id = rc.rule_id and c.csev_id = rc.csev_id
  left join t  on t.code_system = c.code_system  and t.rule_id = c.rule_id
  where ((t.filter_ ->> 'operator') <> 'child-of' or coalesce(r.level, 1) = 1)
    and ((t.filter_ ->> 'operator') <> 'is-not-a'    and r.csev_id is not null or (t.filter_ ->> 'operator') = 'is-not-a'    and r.csev_id is null and rc.csev_id is null)
    and ((t.filter_ ->> 'operator') <> 'descendent-leaf' or not exists (select 1 from r r1 where r1.parent = r.csev_id))
  union
  -- all inverted recursive (generalizes) concepts calculated before
  select c.*, rr.rn, rr.fcnt, rr.level from rr, c
   where c.code_system = rr.code_system
     and c.rule_id = rr.rule_id
     and c.csev_id = rr.csev_id
  union
  -- the filter root concept itself (for is-a, generalizes; excluded for is-not-a)
  select c.*, rc.rn, rc.fcnt, 1 from rc, c
   where c.code_system = rc.code_system
     and c.rule_id = rc.rule_id
     and c.csev_id = rc.csev_id
     and rc.operator <> 'is-not-a'
),
expression_concepts as (
  select rule_id, type, csev_code, obj, fcnt, count(*)
    from expressions
   group by rule_id, type, csev_code, obj, fcnt
),
cs_all as (
  select t.rule_id, t."type", t.code_system, t.original_code_system_version_id, t.code_system_version_id, csev.id csev_id, csev.code csev_code,
         jsonb_build_object('conceptVersionId', csev.id, 'code', csev.code, 'codeSystem', t.code_system, 'codeSystemUri', t.uri, 'baseCodeSystemUri', t.base_uri, 'codeSystemVersionId', t.code_system_version_id) obj,
         row_number() over (partition by t.rule_id, t.code_system, csev.code
                            order by case when evcsvm.code_system_version_id = t.code_system_version_id then 0 else 1 end,
                                     csv.release_date desc) as version_rn
    from rules t,
         terminology.code_system_entity_version csev,
         terminology.entity_version_code_system_version_membership evcsvm,
         terminology.code_system_version csv
   where t.code_system = csev.code_system
     and evcsvm.sys_status = 'A' and csev.sys_status='A'
     and csev.id = evcsvm.code_system_entity_version_id
     and (evcsvm.code_system_version_id = t.code_system_version_id
           or evcsvm.code_system_version_id in (
               select csv2.id
                 from terminology.code_system_version csv2
                where csv2.code_system = t.code_system
                  and csv2.sys_status = 'A'
                  and csv2.release_date <= (select csv3.release_date
                                              from terminology.code_system_version csv3
                                             where csv3.id = t.code_system_version_id)))
     and csv.id = evcsvm.code_system_version_id and csv.sys_status = 'A'
     and t.filters is null and t.concepts is null
     and not exists (
         select 1
           from terminology.entity_version_code_system_version_membership evcsvm_excluded
          where evcsvm_excluded.code_system_version_id = t.code_system_version_id
            and evcsvm_excluded.code_system_entity_version_id = csev.id
            and evcsvm_excluded.sys_status = 'C'
     )
),
cs as (
  select rule_id, "type", code_system, original_code_system_version_id, code_system_version_id, csev_id, csev_code, obj
    from cs_all
   where version_rn = 1
),
vs as (
  select s.* from rules t, lateral terminology.value_set_expand(t.value_set_version_id) s
   where t.value_set_version_id is not null
),
codes as (
  select csev_code, obj
    from cs
   where type='include'
  union
  select concept ->> 'code', concept
    from vs
  union
  select csev_code, obj
    from expression_concepts
   where type='include'
  union
  select csev_code, obj
    from exact_concepts
   where type='include'
  except
  select csev_code, obj
    from cs
   where type='exclude'
  except
  select csev_code, obj
    from expression_concepts
   where type='exclude'
  except
  select csev_code, obj
    from exact_concepts
   where type='exclude'
)
select t.obj, ec.display, ec.additional_designations, ec.order_number,
       case when ec.csev_code is not null then true else false end
  from codes t left outer join exact_concepts ec on t.csev_code = ec.csev_code
 order by ec.order_number;

$function$
;
