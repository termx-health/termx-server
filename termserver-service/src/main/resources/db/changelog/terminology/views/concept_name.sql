create or replace view terminology.concept_name
as with lang as (
         select distinct lang.code
           from terminology.code_system_entity_version lang
          where lang.code_system = 'v3-ietf3066'::text
        ), c as (
         select csv.code_system,
            csev.code,
            csv.status,
            csv.version,
            lang.code as language,
            display.name as display,
            alias.name as alias,
            row_number() over (partition by csv.code_system, csev.code, lang.code order by (
                case
                    when csv.status = 'retired'::text then 1
                    else 0
                end), csv.version desc) as rn
           from lang
             join terminology.code_system_version csv on 1 = 1
             join terminology.code_system_entity_version csev on csev.code_system = csv.code_system
             join terminology.designation display on display.code_system_entity_version_id = csev.id and display.language = lang.code
             join terminology.entity_property epd on epd.id = display.designation_type_id and epd.name = 'display'::text
             left join ( select d.name,
                    d.code_system_entity_version_id,
                    d.language
                   from terminology.designation d,
                    terminology.entity_property ep
                  where d.designation_type_id = ep.id and ep.name = 'alias'::text) alias on alias.code_system_entity_version_id = csev.id and alias.language = lang.code
        )
 select c.code_system,
    c.code,
    c.language,
    c.display,
    c.alias
   from c
  where c.rn = 1;
