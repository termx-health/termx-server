drop function if exists terminology.rule_set_expand(p_rule_set jsonb);
drop function if exists terminology.rule_set_expand(p_value_set_version_id bigint, p_rule_set jsonb);

create or replace function terminology.rule_set_expand(
    p_value_set_version_id bigint,
    p_rule_set jsonb
)
    returns table (
        concept                 jsonb,
        concept_version_id      bigint,
        display                 jsonb,
        additional_designations jsonb,
        order_number            smallint
    )
    language sql
as
$function$
with rule_set as (
    select * from jsonb_to_record(p_rule_set) as x("lockedDate" timestamp, inactive boolean, rules jsonb)
),
    rules as (
        select (jsonb_array_elements(rs.rules) ->> 'id')::text id,
            (jsonb_array_elements(rs.rules) ->> 'type')::text type,
            (jsonb_array_elements(rs.rules) ->> 'codeSystem')::text code_system,
            (jsonb_array_elements(rs.rules) ->> 'codeSystemVersionId')::bigint code_system_version_id,
            (jsonb_array_elements(rs.rules) ->> 'concepts')::jsonb concepts,
            (jsonb_array_elements(rs.rules) ->> 'filters')::jsonb filters,
            (jsonb_array_elements(rs.rules) ->> 'valueSet')::text value_set,
            (jsonb_array_elements(rs.rules) ->> 'valueSetVersionId')::bigint value_set_version_id
        from rule_set rs
    ),
    include_rules as (
        select *
        from rules r
        where r.type = 'include' and (r.code_system is null or not (r.code_system = 'snomed-ct'))
    ),
    exclude_rules as (
        select *
        from rules r
        where r.type = 'exclude' and (r.code_system is null or not (r.code_system = 'snomed-ct'))
    ),
    include_rule_concepts as (
        select ir.code_system, jsonb_array_elements(ir.concepts) c
        from include_rules ir
    ),
    include_rule_filters as (
        select ir.id, jsonb_array_elements(ir.filters) f
        from include_rules ir
    ),
    exclude_rule_filters as (
        select er.id, jsonb_array_elements(er.filters) f
        from exclude_rules er
    ),
    rule_concepts as (
        select jsonb_build_object('id', c.id, 'code', c.code) concept, csev.id concept_version_id, (irc.c -> 'display') display, (irc.c -> 'additionalDesignations') additional_designations, (irc.c -> 'orderNumber')::smallint order_number
        from terminology.concept c
                 left join include_rule_concepts irc on (irc.c -> 'concept' ->> 'id')::bigint = c.id
                 left join terminology.code_system_entity_version csev on  csev.code_system_entity_id = c.id and csev.sys_status = 'A'
        where c.sys_status = 'A' and
            exists(select 1
                   from include_rules ir
                            inner join rule_set rs on true
                   where ir.code_system = c.code_system and
                       exists(select 1
                              from terminology.code_system_version csv
                              where (rs."lockedDate" is not null and tsrange(csv.release_date, csv.expiration_date) @> rs."lockedDate") or
                                  exists(select 1
                                         from terminology.entity_version_code_system_version_membership evcsvm
                                         where evcsvm.sys_status = 'A' and csev.id = evcsvm.code_system_entity_version_id and evcsvm.code_system_version_id = csv.id and csv.id = ir.code_system_version_id)
                           ) and
                       (not exists(select jsonb_array_elements(ir.concepts))
                               or c.id in (select (jsonb_array_elements(ir.concepts) -> 'concept' ->> 'id')::bigint)
                           ) and
                       (not exists(select 1 from include_rule_filters irf where irf.id = ir.id)
                               or exists (select 1 from include_rule_filters irf where irf.id = ir.id and (irf.f -> 'property' ->> 'name')::text = 'code' and (coalesce(irf.f ->> 'value', '') = '' or csev.code = (irf.f ->> 'value')::text or csev.code = substring(csev.code, (irf.f ->> 'value')::text)))
                               or exists (select 1 from terminology.entity_property_value epv
                                   where csev.id = epv.code_system_entity_version_id and exists(select 1 from include_rule_filters irf
                                         where irf.id = ir.id and ((irf.f -> 'property' ->> 'id') is null or (irf.f -> 'property' ->> 'id')::bigint = epv.entity_property_id) and
                                               (coalesce(irf.f ->> 'value', '') = '' or to_jsonb((irf.f ->> 'value')::text) = epv.value or (irf.f ->> 'value')::text = (epv.value ->> 'code')::text)))
                               or exists (select 1 from terminology.designation d
                                   where csev.id = d.code_system_entity_version_id and exists(select 1 from include_rule_filters irf
                                         where irf.id = ir.id and ((irf.f -> 'property' ->> 'id') is null or (irf.f -> 'property' ->> 'id')::bigint = d.designation_type_id) and
                                               (coalesce(irf.f ->> 'value', '') = '' or (irf.f ->> 'value')::text = d.name)))
--                                or exists (with recursive associations as (
--                                     select csa.source_code_system_entity_version_id, csa.target_code_system_entity_version_id  from terminology.code_system_association csa where csa.sys_status = 'A' and exists(select 1 from include_rule_filters irf
--                                          where irf.id = ir.id and (irf.f -> 'property' ->> 'name')::text = 'concept' and (irf.f ->> 'operator')::text = csa.association_type and
--                                               (coalesce(irf.f ->> 'value', '') = '' or exists (select 1 from terminology.code_system_entity_version csev1 where csev1.sys_status = 'A' and csev1.id = csa.target_code_system_entity_version_id and csev1.code = (irf.f ->> 'value')::text)))
--                                     union select csa1.source_code_system_entity_version_id, csa1.target_code_system_entity_version_id from terminology.code_system_association csa1 inner join associations a on a.source_code_system_entity_version_id = csa1.target_code_system_entity_version_id)
--                                     select 1 from associations where (csev.id = source_code_system_entity_version_id or csev.id = target_code_system_entity_version_id))
                           )
                ) and
            not exists(select 1
                       from exclude_rules er
                                inner join rule_set rs on true
                       where er.code_system = c.code_system and
                           exists(select 1
                                  from terminology.code_system_version csv
                                  where (rs."lockedDate" is not null and tsrange(csv.release_date, csv.expiration_date) @> rs."lockedDate") or
                                      exists(select 1
                                             from terminology.entity_version_code_system_version_membership evcsvm
                                             where evcsvm.sys_status = 'A' and csev.id = evcsvm.code_system_entity_version_id and evcsvm.code_system_version_id = csv.id and csv.id = er.code_system_version_id)
                               ) and
                           (not exists(select jsonb_array_elements(er.concepts))
                                   or c.id in (select (jsonb_array_elements(er.concepts) -> 'concept' ->> 'id')::bigint)
                               ) and
                           (not exists(select 1 from exclude_rule_filters erf where erf.id = er.id)
                                   or exists (select 1 from exclude_rule_filters erf where erf.id = er.id and (erf.f -> 'property' ->> 'name')::text = 'code' and (coalesce(erf.f ->> 'value', '') = '' or csev.code = (erf.f ->> 'value')::text or csev.code = substring(csev.code, (erf.f ->> 'value')::text)))
                                   or exists (select 1 from terminology.entity_property_value epv
                                       where csev.id = epv.code_system_entity_version_id and exists(select 1 from exclude_rule_filters erf
                                             where erf.id = er.id and ((erf.f -> 'property' ->> 'id') is null or (erf.f -> 'property' ->> 'id')::bigint = epv.entity_property_id) and
                                                   (coalesce(erf.f ->> 'value', '') = '' or to_jsonb((erf.f ->> 'value')::text) = epv.value or (erf.f ->> 'value')::text = (epv.value ->> 'code')::text)))
                                   or exists (select 1 from terminology.designation d
                                       where csev.id = d.code_system_entity_version_id and exists(select 1 from exclude_rule_filters erf
                                             where erf.id = er.id and ((erf.f -> 'property' ->> 'id') is null or (erf.f -> 'property' ->> 'id')::bigint = d.designation_type_id) and
                                                   (coalesce(erf.f ->> 'value', '') = '' or (erf.f ->> 'value')::text = d.name)))
--                                    or exists (with recursive associations as (
--                                         select csa.source_code_system_entity_version_id, csa.target_code_system_entity_version_id  from terminology.code_system_association csa where csa.sys_status = 'A' and exists(select 1 from exclude_rule_filters erf
--                                                 where erf.id = er.id and (erf.f -> 'property' ->> 'name')::text = 'concept' and (erf.f ->> 'operator')::text = csa.association_type and
--                                                     (coalesce(erf.f ->> 'value', '') = '' or exists (select 1 from terminology.code_system_entity_version csev1 where csev1.sys_status = 'A' and csev1.id = csa.target_code_system_entity_version_id and csev1.code = (erf.f ->> 'value')::text)))
--                                         union select csa1.source_code_system_entity_version_id, csa1.target_code_system_entity_version_id from terminology.code_system_association csa1 inner join associations a on a.source_code_system_entity_version_id = csa1.target_code_system_entity_version_id)
--                                         select 1 from associations where (csev.id = source_code_system_entity_version_id or csev.id = target_code_system_entity_version_id))
                               )
                )
    ),
    concepts as (
       select jsonb_build_object('id', null, 'code', irc.c -> 'concept' ->> 'code', 'codeSystem', irc.code_system) concept, null::bigint concept_version_id,(irc.c -> 'display') display, (irc.c -> 'additionalDesignations') additional_designations, (irc.c -> 'orderNumber')::smallint order_number
        from include_rule_concepts irc
        where not exists (select 1 from rule_concepts rc where (rc.concept ->> 'code') = (irc.c -> 'concept' ->> 'code'))
    ),
    value_set_concepts as (
        select s.* from include_rules ir, lateral terminology.value_set_expand(ir.value_set_version_id) s
    )
select *
from (select *
      from (select * from concepts union all select rc.concept, rc.concept_version_id, rc.display, rc.additional_designations, rc.order_number from rule_concepts rc) u1
      union all
      select *
      from value_set_concepts) u2 order by order_number;
$function$
;
