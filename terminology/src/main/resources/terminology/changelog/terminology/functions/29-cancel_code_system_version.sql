drop function if exists terminology.cancel_code_system_version(p_code_system_version_id bigint);

create or replace function terminology.cancel_code_system_version(p_code_system_version_id bigint)
    returns boolean
    language plpgsql
as
$function$
declare 
  l_cs text;
  l_cnt bigint;
begin 
select code_system into l_cs 
  from terminology.code_system_version
 where id = p_code_system_version_id;

with memb as (
  select m.id, m.code_system_entity_version_id csev_id, csv.code_system, m.code_system_version_id
    from terminology.entity_version_code_system_version_membership m
         inner join terminology.code_system_version csv on m.code_system_version_id = csv.id and csv.sys_status='A'
   where m.code_system_version_id = p_code_system_version_id
     and m.sys_status = 'A'
),
csev as (
	select distinct m.csev_id id, coalesce(
	 			 ( select 1 
		         from terminology.entity_version_code_system_version_membership m2
		              inner join terminology.code_system_version csv2 on m2.code_system_version_id = csv2.id
		                     and csv2.code_system = m.code_system and csv2.sys_status='A'
		                     and m.csev_id = m2.code_system_entity_version_id
		                     and m.id <> m2.id 
		                     and m.code_system_version_id <> m2.code_system_version_id
		         where m2.sys_status = 'A' limit 1), 0) as reused
	  from memb m
),
assoc as ( 
  update terminology.code_system_association
     set sys_status = 'D'
   where sys_status <> 'D'
     and (source_code_system_entity_version_id in (select id from csev) and source_code_system_entity_version_id in (select id from csev where reused=0)
      or target_code_system_entity_version_id in (select id from csev) and target_code_system_entity_version_id in (select id from csev where reused=0))
returning id    
),
mmm as (
	update terminology.entity_version_code_system_version_membership
	   set sys_status = 'D'
	 where sys_status <> 'D'
	   and id in (select id from memb)
	returning id
), 
pv as (
	update terminology.entity_property_value pv
	   set sys_status = 'D'
	 where pv.code_system_entity_version_id in (select id from csev where reused=0) 
	returning id
),  
d as (
	update terminology.designation d
     set sys_status = 'D'
   where d.code_system_entity_version_id in (select id from csev where reused=0)
	returning id
),  
ev as (
  update terminology.code_system_entity_version csev
     set sys_status = 'D'
   where csev.id in (select id from csev where reused=0)
	returning id
),  
e as (
  update terminology.code_system_entity cse
     set sys_status = 'D'
   where sys_status = 'A'
     and cse.code_system = l_cs
     and not exists(select 1 from terminology.code_system_entity_version csev 
                     where csev.code_system_entity_id = cse.id and csev.sys_status = 'A')
	returning id
),  
c as (
  update terminology.concept c
     set sys_status = 'D'
   where sys_status = 'A'
     and c.code_system = l_cs
     and not exists(select 1 from terminology.code_system_entity cse 
                     where cse.id = c.id and cse.sys_status = 'A')
	returning id
),  
v as (
  update terminology.code_system_version
     set sys_status = 'D'
   where sys_status = 'A'
     and id = p_code_system_version_id
	returning id
)
select 1 into l_cnt;

return true; 

end;

$function$
;

