create or replace view terminology.ts_concept
as
with lang as (
    select distinct code from terminology.code_system_entity_version lang
    where lang.code_system='v3-ietf3066'
),
    c as (
        select csv.code_system, csev.code, csv.status, csv.version, lang.code as language, display.name as display, alias.name as alias,
                    row_number() over (partition by csv.code_system, csev.code, lang.code
                order by case when csv.status='retiried' then 1 else 0 end, csv.version desc) rn
        from lang
                 inner join terminology.code_system_version csv on 1=1
                 inner join terminology.code_system_entity_version csev on csev.code_system = csv.code_system
                 inner join terminology.designation display on display.code_system_entity_version_id = csev.id and display.language=lang.code
                 inner join terminology.entity_property epd on epd.id = display.designation_type_id and epd.name='display'
                 left outer join (
            select d.name, d.code_system_entity_version_id, d.language
            from terminology.designation d, terminology.entity_property ep
            where d.designation_type_id = ep.id and ep.name = 'alias'
        ) alias on alias.code_system_entity_version_id = csev.id and alias.language=lang.code
    )
select code_system, code, language, display, alias
from c
where rn=1
;
