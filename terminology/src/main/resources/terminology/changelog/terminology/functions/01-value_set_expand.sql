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
with rule_set as (
    select vsvrs.*
    from terminology.value_set_version_rule_set vsvrs
             inner join terminology.value_set_version vsv on vsv.id = vsvrs.value_set_version_id and vsv.sys_status = 'A'
    where vsv.id = p_value_set_version_id and vsvrs.sys_status = 'A'
    limit 1
    ),
    include_rules as (
        select *
        from terminology.value_set_version_rule vsvr
        where vsvr.rule_set_id in (select id from rule_set) and vsvr."type" = 'include' and vsvr.sys_status = 'A' and (vsvr.code_system is null or not (vsvr.code_system = 'snomed-ct'))
    ),
    exclude_rules as (
        select *
        from terminology.value_set_version_rule vsvr
        where vsvr.rule_set_id in (select id from rule_set) and vsvr."type" = 'exclude' and vsvr.sys_status = 'A' and (vsvr.code_system is null or not (vsvr.code_system = 'snomed-ct'))
    ),
    include_rule_concepts as (
        select ir.id, ir.code_system, jsonb_array_elements(ir.concepts) c
        from include_rules ir
    ),
    exclude_rule_concepts as (
        select er.id, er.code_system, jsonb_array_elements(er.concepts) c
        from exclude_rules er
    ),
    include_rule_filters as (
        select ir.id, jsonb_array_elements(ir.filters) f
        from include_rules ir
    ),
    exclude_rule_filters as (
        select er.id, jsonb_array_elements(er.filters) f
        from exclude_rules er
    ),
    include_rule_filter_concepts as (
        select jsonb_build_object('conceptVersionId', csev.id, 'code', csev.code, 'codeSystem', csev.code_system, 'codeSystemUri', (select cs.uri from terminology.code_system cs where cs.sys_status = 'A' and cs.id = csev.code_system)) concept, null::jsonb display, null::jsonb additional_designations, null::smallint order_number
        from terminology.code_system_entity_version csev
            where csev.sys_status = 'A' and exists(select 1 from include_rules ir
-- filter by codesystem and version
                        where ir.code_system = csev.code_system
                        and exists(select 1 from terminology.entity_version_code_system_version_membership evcsvm where evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = ir.code_system_version_id and csev.id = evcsvm.code_system_entity_version_id)
-- filter by code
                        and (exists(select 1 from include_rule_filters irf where irf.id = ir.id and (
                            ((irf.f -> 'property' ->> 'name')::text = 'code' and (csev.code = (irf.f ->> 'value')::text or csev.code = substring(csev.code, (irf.f ->> 'value')::text)))
-- filter by property
                            or exists (select 1 from terminology.entity_property_value epv where epv.sys_status = 'A' and csev.id = epv.code_system_entity_version_id
                                                            and exists (select 1 from terminology.entity_property ep where ep.sys_status = 'A' and (irf.f -> 'property' ->> 'name')::text = ep.name and ep.id = epv.entity_property_id)
                                                            and (coalesce(irf.f ->> 'value', '') = '' or to_jsonb((irf.f ->> 'value')::text) = epv.value or (irf.f ->> 'value')::text = (epv.value ->> 'code')::text))
-- filter by designation
                            or exists (select 1 from terminology.designation d where d.sys_status = 'A' and csev.id = d.code_system_entity_version_id
                                                            and exists (select 1 from terminology.entity_property ep where ep.sys_status = 'A' and (irf.f -> 'property' ->> 'name')::text = ep.name and ep.id = d.designation_type_id)
                                                            and (coalesce(irf.f ->> 'value', '') = '' or (irf.f ->> 'value')::text = d.name))
-- filter by association
                            or exists (with recursive associations as (
                                                select csa.source_code_system_entity_version_id, csa.target_code_system_entity_version_id
                                                from terminology.code_system_association csa where csa.sys_status = 'A'
                                                            and (irf.f ->> 'operator')::text = csa.association_type
                                                            and (coalesce(irf.f ->> 'value', '') = '' or exists (select 1 from terminology.code_system_entity_version csev1 where csev1.sys_status = 'A' and csev1.id = csa.target_code_system_entity_version_id and csev1.code = (irf.f ->> 'value')::text))
                                                union select csa1.source_code_system_entity_version_id, csa1.target_code_system_entity_version_id
                                                from terminology.code_system_association csa1 inner join associations a on a.source_code_system_entity_version_id = csa1.target_code_system_entity_version_id)
                                       select 1 from associations where (csev.id = source_code_system_entity_version_id or csev.id = target_code_system_entity_version_id))))
-- all concepts included
                        or (not exists (select 1 from include_rule_filters irf where irf.id = ir.id) and not exists(select 1 from include_rule_concepts irc where irc.id = ir.id))))
    ),
    exclude_rule_filter_concepts as (
        select csev.code code, csev.code_system code_system
        from terminology.code_system_entity_version csev
            where csev.sys_status = 'A' and exists(select 1 from exclude_rules er
                        where er.code_system = csev.code_system
                        and exists(select 1 from terminology.entity_version_code_system_version_membership evcsvm where evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = er.code_system_version_id and csev.id = evcsvm.code_system_entity_version_id)
                        and (exists(select 1 from exclude_rule_filters erf where erf.id = er.id and (
                            ((erf.f -> 'property' ->> 'name')::text = 'code' and csev.code = (erf.f ->> 'value')::text)
                            or exists (select 1 from terminology.entity_property_value epv where epv.sys_status = 'A' and csev.id = epv.code_system_entity_version_id
                                                            and exists (select 1 from terminology.entity_property ep where ep.sys_status = 'A' and (erf.f -> 'property' ->> 'name')::text = ep.name and ep.id = epv.entity_property_id)
                                                            and (coalesce(erf.f ->> 'value', '') = '' or to_jsonb((erf.f ->> 'value')::text) = epv.value or (erf.f ->> 'value')::text = (epv.value ->> 'code')::text))
                            or exists (select 1 from terminology.designation d where d.sys_status = 'A' and csev.id = d.code_system_entity_version_id
                                                            and exists (select 1 from terminology.entity_property ep where ep.sys_status = 'A' and (erf.f -> 'property' ->> 'name')::text = ep.name and ep.id = d.designation_type_id)
                                                            and (coalesce(erf.f ->> 'value', '') = '' or (erf.f ->> 'value')::text = d.name))
                            or exists (with recursive associations as (
                                                select csa.source_code_system_entity_version_id, csa.target_code_system_entity_version_id
                                                from terminology.code_system_association csa where csa.sys_status = 'A'
                                                            and (erf.f ->> 'operator')::text = csa.association_type
                                                            and (coalesce(erf.f ->> 'value', '') = '' or exists (select 1 from terminology.code_system_entity_version csev1 where csev1.sys_status = 'A' and csev1.id = csa.target_code_system_entity_version_id and csev1.code = (erf.f ->> 'value')::text))
                                                union select csa1.source_code_system_entity_version_id, csa1.target_code_system_entity_version_id
                                                from terminology.code_system_association csa1 inner join associations a on a.source_code_system_entity_version_id = csa1.target_code_system_entity_version_id)
                                       select 1 from associations where (csev.id = source_code_system_entity_version_id or csev.id = target_code_system_entity_version_id))))
                        or (not exists (select 1 from exclude_rule_filters erf where erf.id = er.id) and not exists(select 1 from exclude_rule_concepts erc where erc.id = er.id))))
    ),
    prepared_include_rule_concepts as (
        select jsonb_build_object('conceptVersionId', csev.id, 'code', irc.c -> 'concept' ->> 'code', 'codeSystem', irc.code_system, 'codeSystemUri', (select cs.uri from terminology.code_system cs where cs.sys_status = 'A' and cs.id = irc.code_system)) concept,(irc.c -> 'display') display, (irc.c -> 'additionalDesignations') additional_designations, (irc.c -> 'orderNumber')::smallint order_number
        from include_rule_concepts irc left join terminology.code_system_entity_version csev on (irc.c -> 'concept' ->> 'code')::text = csev.code
            and csev.sys_status = 'A' and exists(select 1 from include_rules ir
                        where ir.code_system = csev.code_system and ir.id = irc.id
                        and exists(select 1 from terminology.entity_version_code_system_version_membership evcsvm where evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = ir.code_system_version_id and csev.id = evcsvm.code_system_entity_version_id))
    ),
    all_include_concepts as (
       select  pirc.concept, pirc.display, pirc.additional_designations, pirc.order_number, true::boolean enumerated from prepared_include_rule_concepts pirc
       union all
       select irfc.concept, irfc.display, irfc.additional_designations, irfc.order_number, false::boolean enumerated from include_rule_filter_concepts irfc
    ),
    all_exclude_concepts as (
       select erc.c -> 'concept' ->> 'code' code, erc.code_system code_system from exclude_rule_concepts erc
       union all
       select erfc.code, erfc.code_system from exclude_rule_filter_concepts erfc
    ),
    concepts as (
       select * from all_include_concepts aic where not exists (select 1 from all_exclude_concepts aec where aec.code = (aic.concept ->> 'code') and aec.code_system = (aic.concept ->> 'codeSystem'))
    ),
    value_set_concepts as (
        select s.* from include_rules ir, lateral terminology.value_set_expand(ir.value_set_version_id) s
    )
select * from (
    select * from concepts
    union all
    select * from value_set_concepts) u2 order by order_number;
$function$
;
