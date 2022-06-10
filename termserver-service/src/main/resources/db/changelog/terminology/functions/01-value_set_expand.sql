drop function if exists value_set_expand(p_value_set text, p_value_set_version text, p_rule_set jsonb);

create or replace function value_set_expand(
    p_value_set text default null,
    p_value_set_version text default null,
    p_rule_set jsonb default null
)
    returns table (
        id                      bigint,
        order_nr                int,
        concept_id              bigint,
        display                 bigint,
        additional_designations bigint[]
    )
    language plpgsql
as
$function$
begin
    return query
        with vs_rule_set as (
            select vsv.rule_set rs
            from value_set_version vsv
                     inner join value_set vs on vs.id = vsv.value_set and vs.sys_status = 'A'
            where vsv.sys_status = 'A' and vsv.version = p_value_set_version and vs.id = p_value_set
            limit 1
        ),
            rule_set as (
                select * from (select * from vs_rule_set union all select * from (values (p_rule_set::jsonb)) rs where not exists(select 1 from vs_rule_set)) u1
            ),
            include_rules as (
                select jsonb_array_elements(rs -> 'includeRules') ir
                from rule_set
            ),
            include_rule_concepts as (
                select jsonb_array_elements(ir -> 'concepts') c
                from include_rules
            ),
            value_set_concepts as (
                select s.* from include_rules, lateral value_set_expand(ir ->> 'valueSet', ir ->> 'valueSetVersion', null) s
            ),
            all_concepts as (
                select null::bigint id, null::int order_nr, (irc.c -> 'concept' ->> 'id')::bigint concept_id, (irc.c -> 'display' ->> 'id')::bigint display,
                    array(select jsonb_array_elements(irc.c -> 'additionalDesignations') ->> 'id')::bigint[] additional_designations
                from include_rule_concepts irc
                union all
                select *
                from value_set_concepts
            ),
            exclude_rules as (
                select jsonb_array_elements(rs -> 'excludeRules') er
                from rule_set
            ),
            vs_concepts as (
                select cvsvm.id, cvsvm.order_nr, cvsvm.concept_id, cvsvm.display, cvsvm.additional_designations
                from concept_value_set_version_membership cvsvm
                         inner join value_set_version vsv on vsv.id = cvsvm.value_set_version_id and vsv.sys_status = 'A'
                         inner join value_set vs on vs.id = vsv.value_set and vs.sys_status = 'A'
                where cvsvm.sys_status = 'A' and vsv.version = '4.3.0' and vs.id = 'languages'
            ),
            rule_set_concepts as (
                select ac.id, ac.order_nr, c.id conept_id, ac.display, ac.additional_designations
                from concept c
                         left join all_concepts ac on ac.concept_id = c.id
                where exists(select 1
                             from include_rules
                             where (ir ->> 'codeSystem' is not null and (ir ->> 'codeSystemVersion' is not null or ir ->> 'lockedDate' is not null)
                                     or ir ->> 'valueSet' is not null and ir ->> 'valueSetVersion' is not null) and
                                 (ir ->> 'codeSystem' is null or ir ->> 'codeSystemVersion' is null and ir ->> 'lockedDate' is null
                                         or (exists(select 1
                                                    from code_system_version csv
                                                             inner join code_system cs on cs.id = csv.code_system and cs.sys_status = 'A'
                                                             inner join entity_version_code_system_version_membership evcsvm
                                                    on evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A'
                                                             inner join code_system_entity_version csev
                                                    on csev.id = evcsvm.code_system_entity_version_id and csev.sys_status = 'A'
                                                             inner join code_system_entity cse on cse.id = csev.code_system_entity_id and cse.sys_status = 'A'
                                                    where csv.sys_status = 'A' and cse.id = c.id and
                                                        (ir ->> 'codeSystemVersion' is null or csv.version = ir ->> 'codeSystemVersion') and
                                                        cs.id = ir ->> 'codeSystem' and
                                                        (ir ->> 'lockedDate' is null
                                                                or tsrange(csv.release_date, csv.expiration_date) @> (ir ->> 'lockedDate')::timestamp))
                                             and (not exists(select jsonb_array_elements(ir -> 'concepts'))
                                                 or exists(select 1
                                                           where c.id in (select (jsonb_array_elements(ir -> 'concepts') -> 'concept' ->> 'id')::bigint)))
                                             and (not exists(select jsonb_array_elements(ir -> 'filters'))
                                                 or exists(select jsonb_array_elements(ir -> 'filters') where 1 = 1)))) and
                                 (ir ->> 'valueSet' is null or ir ->> 'valueSetVersion' is null
                                         or exists(select 1
                                                   from value_set_expand(ir ->> 'valueSet', ir ->> 'valueSetVersion', null) vse
                                                   where vse.concept_id = c.id)
                                     )
                    ) and
                    not exists(select 1
                               from exclude_rules
                               where (er ->> 'codeSystem' is not null and (er ->> 'codeSystemVersion' is not null or er ->> 'lockedDate' is not null)
                                       or er ->> 'valueSet' is not null and er ->> 'valueSetVersion' is not null) and
                                   (er ->> 'codeSystem' is not null and (er ->> 'codeSystemVersion' is not null or er ->> 'lockedDate' is not null)
                                           and exists(select 1
                                                      from code_system_version csv
                                                               inner join code_system cs on cs.id = csv.code_system and cs.sys_status = 'A'
                                                               inner join entity_version_code_system_version_membership evcsvm
                                                      on evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A'
                                                               inner join code_system_entity_version csev
                                                      on csev.id = evcsvm.code_system_entity_version_id and csev.sys_status = 'A'
                                                               inner join code_system_entity cse on cse.id = csev.code_system_entity_id and cse.sys_status = 'A'
                                                      where csv.sys_status = 'A' and cse.id = c.id and
                                                          (er ->> 'codeSystemVersion' is null or csv.version = er ->> 'codeSystemVersion') and
                                                          cs.id = er ->> 'codeSystem' and
                                                          (er ->> 'lockedDate' is null
                                                                  or tsrange(csv.release_date, csv.expiration_date) @> (er ->> 'lockedDate')::timestamp))
                                           and (not exists(select jsonb_array_elements(er -> 'concepts'))
                                               or exists(select 1
                                                         where c.id in (select (jsonb_array_elements(er -> 'concepts') -> 'concept' ->> 'id')::bigint)))
                                           and (not exists(select jsonb_array_elements(er -> 'filters'))
                                               or exists(select jsonb_array_elements(er -> 'filters') where 1 = 1))
                                           or er ->> 'valueSet' is not null and er ->> 'valueSetVersion' is not null
                                               and exists(select 1
                                                          from value_set_expand(er ->> 'valueSet', er ->> 'valueSetVersion', null) vse
                                                          where vse.concept_id = c.id)
                                       )
                        )
            )
        select *
        from (select * from vs_concepts union all select * from rule_set_concepts where not exists(select 1 from vs_concepts)) u2;
end;
$function$
;
