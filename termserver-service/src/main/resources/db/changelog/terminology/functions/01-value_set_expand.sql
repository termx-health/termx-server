drop function if exists terminology.value_set_expand(p_value_set_version_id bigint);

create or replace function terminology.value_set_expand(
    p_value_set_version_id bigint
)
    returns table (
        id                      bigint,
        concept                 jsonb,
        display                 jsonb,
        additional_designations jsonb
    )
    language sql
as
$function$
with rule_set as (
    select vsvrs.*
    from terminology.value_set_version_rule_set vsvrs
             inner join terminology.value_set_version vsv on vsv.id = vsvrs.value_set_version_id and vsv.sys_status = 'A'
    where vsv.id = p_value_set_version_id and vsvrs.sys_status = 'A'
    limit 1
),
    concepts as (
        select vsvc.id, vsvc.concept, vsvc.display, vsvc.additional_designations
        from terminology.value_set_version_concept vsvc
        where vsvc.value_set_version_id = p_value_set_version_id and vsvc.sys_status = 'A'
    ),
    include_rules as (
        select *
        from terminology.value_set_version_rule vsvr
        where vsvr.rule_set_id in (select id from rule_set) and vsvr."type" = 'include' and vsvr.sys_status = 'A'
    ),
    exclude_rules as (
        select *
        from terminology.value_set_version_rule vsvr
        where vsvr.rule_set_id in (select id from rule_set) and vsvr."type" = 'exclude' and vsvr.sys_status = 'A'
    ),
    include_rule_concepts as (
        select jsonb_array_elements(ir.concepts) c
        from include_rules ir
    ),
    include_rule_filters as (
        select jsonb_array_elements(ir.filters) f
        from include_rules ir
    ),
    exclude_rule_filters as (
        select jsonb_array_elements(er.filters) f
        from exclude_rules er
    ),
    rule_concepts as (
        select jsonb_build_object('id', c.id, 'code', c.code) concept, (irc.c -> 'display') display, (irc.c -> 'additionalDesignations') additional_designations
        from terminology.concept c
                 left join include_rule_concepts irc on (irc.c -> 'concept' ->> 'id')::bigint = c.id
        where c.sys_status = 'A' and
            exists(select 1
                   from include_rules ir
                            inner join rule_set rs on rs.id = ir.rule_set_id
                   where ir.code_system = c.code_system and
                       exists(select 1
                              from terminology.code_system_version csv
                              where (rs.locked_date is not null and tsrange(csv.release_date, csv.expiration_date) @> rs.locked_date) or
                                  exists(select 1
                                         from terminology.entity_version_code_system_version_membership evcsvm
                                                  inner join terminology.code_system_entity_version csev
                                         on csev.id = evcsvm.code_system_entity_version_id and csev.sys_status = 'A' and csev.code_system_entity_id = c.id
                                         where evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = csv.id and csv.id = ir.code_system_version_id)
                           ) and
                       (not exists(select jsonb_array_elements(ir.concepts))
                               or c.id in (select (jsonb_array_elements(ir.concepts) -> 'concept' ->> 'id')::bigint)
                           ) and
                       (not exists(select 1 from include_rule_filters)
                               or exists (select 1 from terminology.entity_property_value epv
                                   inner join terminology.code_system_entity_version csev on csev.id = epv.code_system_entity_version_id and csev.sys_status = 'A'
                                   where csev.code_system_entity_id = c.id and exists(select 1 from include_rule_filters irf
                                        where ((irf.f -> 'property' ->> 'id') is null or (irf.f -> 'property' ->> 'id')::bigint = epv.entity_property_id) and
                                              (coalesce(irf.f ->> 'value', '') = '' or (irf.f ->> 'value')::jsonb = epv.value)))
                               or exists (select 1 from terminology.designation d
                                   inner join terminology.code_system_entity_version csev on csev.id = d.code_system_entity_version_id and csev.sys_status = 'A'
                                   where csev.code_system_entity_id = c.id and exists(select 1 from include_rule_filters irf
                                        where ((irf.f -> 'property' ->> 'id') is null or (irf.f -> 'property' ->> 'id')::bigint = d.designation_type_id) and
                                              (coalesce(irf.f ->> 'value', '') = '' or (irf.f ->> 'value')::text = d.name)))
                           )
                ) and
            not exists(select 1
                       from exclude_rules er
                                inner join rule_set rs on rs.id = er.rule_set_id
                       where er.code_system = c.code_system and
                           exists(select 1
                                  from terminology.code_system_version csv
                                  where (rs.locked_date is not null and tsrange(csv.release_date, csv.expiration_date) @> rs.locked_date) or
                                      exists(select 1
                                             from terminology.entity_version_code_system_version_membership evcsvm
                                                      inner join terminology.code_system_entity_version csev
                                             on csev.id = evcsvm.code_system_entity_version_id and csev.sys_status = 'A' and csev.code_system_entity_id = c.id
                                             where evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = csv.id and csv.id = er.code_system_version_id)
                               ) and
                           (not exists(select jsonb_array_elements(er.concepts))
                                   or c.id in (select (jsonb_array_elements(er.concepts) -> 'concept' ->> 'id')::bigint)
                               ) and
                           (not exists(select 1 from exclude_rule_filters)
                                   or exists (select 1 from terminology.entity_property_value epv
                                       inner join terminology.code_system_entity_version csev on csev.id = epv.code_system_entity_version_id and csev.sys_status = 'A'
                                       where csev.code_system_entity_id = c.id and exists(select 1 from exclude_rule_filters erf
                                             where ((erf.f -> 'property' ->> 'id') is null or (erf.f -> 'property' ->> 'id')::bigint = epv.entity_property_id) and
                                                   (coalesce(erf.f ->> 'value', '') = '' or (erf.f ->> 'value')::jsonb = epv.value)))
                                   or exists (select 1 from terminology.designation d
                                       inner join terminology.code_system_entity_version csev on csev.id = d.code_system_entity_version_id and csev.sys_status = 'A'
                                       where csev.code_system_entity_id = c.id and exists(select 1 from exclude_rule_filters erf
                                              where ((erf.f -> 'property' ->> 'id') is null or (erf.f -> 'property' ->> 'id')::bigint = d.designation_type_id) and
                                                    (coalesce(erf.f ->> 'value', '') = '' or (erf.f ->> 'value')::text = d.name)))
                               )
                )
    ),
    value_set_concepts as (
        select s.* from include_rules ir, lateral terminology.value_set_expand(ir.value_set_version_id) s
    )
select *
from (select *
      from (select * from concepts union all select null, rc.concept, rc.display, rc.additional_designations from rule_concepts rc) u1
      union all
      select *
      from value_set_concepts) u2;
$function$
;
