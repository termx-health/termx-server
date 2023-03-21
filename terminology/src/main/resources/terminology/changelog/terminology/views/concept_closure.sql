drop materialized view if exists terminology.concept_closure;

-- think about reducing of amount only to the code systems that have associations
create materialized view terminology.concept_closure
as
with recursive r as (
  select c.id, c.code_system, c.code, null::bigint parent_id, 0 as depth, ('.'::text || c.code) || '.'::text as path
    from terminology.code_system_entity_version c
   where c.sys_status='A'
     and not exists(select 1 from terminology.code_system_association csa
                     where csa.source_code_system_entity_version_id = c.id and csa.sys_status='A')
  union
  select c.id, c.code_system, c.code, csa.source_code_system_entity_version_id, r.depth + 1, (r.path || c.code) || '.'::text
    from r
         inner join terminology.code_system_association csa on csa.target_code_system_entity_version_id = r.id and csa.sys_status='A' and csa.status<>'retired'
         inner join terminology.code_system_entity_version c on csa.source_code_system_entity_version_id = c.id and c.sys_status='A' and c.status<>'retired'
)
select h1.code_system,
  h1.id as parent_id,
  h1.code as parent_code,
  h2.id as child_id,
  h2.code as child_code,
  h2.depth - h1.depth as distance,
  h2.path
 from r h1, r h2
where h1.code_system=h2.code_system
  and h2.path ~~ (h1.path || '%'::text)
with data;

create index concept_closure_cs_child on terminology.concept_closure(code_system, child_code);
