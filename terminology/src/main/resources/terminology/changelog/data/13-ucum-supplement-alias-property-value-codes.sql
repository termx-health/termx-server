--liquibase formatted sql

--changeset termx:ucum-supplement-alias-property-value-codes
--comment: Canonicalise UCUM unit codes held in coding property values (rewrite supplement alias forms to the base UCUM code)

-- A UCUM supplement (e.g. ucum-lt) carries the CANONICAL UCUM code in
-- code_system_entity_version.code and the older, non-canonical form as an `alias`
-- designation (e.g. mg/L <= mg/l, 10*9/L <= 10^9/l, [CFU] <= KFV, {#} <= skaičius).
-- Coding property values imported before the supplement existed may still hold the
-- alias form, which resolves to no UCUM concept ("missing" units in the UI). Rewrite
-- each such value's `code` to the canonical UCUM code.
--
-- Scope/safety:
--  * only `Coding` property values whose codeSystem references UCUM (the base `ucum`
--    or any UCUM supplement) are touched;
--  * the alias -> canonical map is derived from ALL supplements of `ucum`, so it is
--    deployment-agnostic (no hardcoded supplement id);
--  * only UNAMBIGUOUS aliases (alias name -> exactly one canonical code) are migrated;
--  * `value.display` and audit columns are left untouched, mirroring
--    terminology.change_code_system_id (the coding-enrichment job / live UI resolution
--    recomputes the display);
--  * a no-op where no UCUM supplement or no matching values exist.
with ucum_systems as (
  select id
    from terminology.code_system
   where sys_status = 'A'
     and (id = 'ucum' or (content = 'supplement' and base_code_system = 'ucum'))
),
ucum_alias as (
  select d.name as alias_code, min(csev.code) as canonical_code
    from terminology.code_system cs
         join terminology.code_system_entity_version csev
              on csev.code_system = cs.id and csev.sys_status = 'A'
         join terminology.designation d
              on d.code_system_entity_version_id = csev.id and d.sys_status = 'A'
         join terminology.entity_property ept
              on ept.id = d.designation_type_id and ept.name = 'alias' and ept.sys_status = 'A'
   where cs.sys_status = 'A' and cs.content = 'supplement' and cs.base_code_system = 'ucum'
     and d.name is not null and d.name <> csev.code
   group by d.name
  having count(distinct csev.code) = 1
)
update terminology.entity_property_value epv
   set value = jsonb_set(epv.value, '{code}', to_jsonb(ua.canonical_code), false)
  from ucum_alias ua,
       terminology.entity_property ep
 where ep.id = epv.entity_property_id
   and ep.type = 'Coding'
   and epv.sys_status = 'A'
   and epv.value ->> 'code' = ua.alias_code
   and epv.value ->> 'codeSystem' in (select id from ucum_systems);
