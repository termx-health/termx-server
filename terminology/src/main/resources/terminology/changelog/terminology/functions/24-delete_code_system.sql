drop function if exists terminology.delete_code_system(p_code_system text);
drop function if exists terminology.delete_code_system(p_code_system text, force boolean);

create or replace function terminology.delete_code_system(p_code_system text, force boolean default false)
    returns boolean
    language plpgsql
as
$function$
declare 
  l_txt text;
begin 
raise notice 'Deleting code system: %', p_code_system;

-- clean deleteted rules
delete from terminology.value_set_version_rule vsvr
 where vsvr.code_system = p_code_system
   and vsvr.sys_status = 'D';

-- if dependant VS exists  
select string_agg(vsv.value_set,',')  into l_txt
  from terminology.value_set_version_rule vsvr
       inner join terminology.value_set_version_rule_set rs on rs.id = vsvr.rule_set_id
       inner join terminology.value_set_version vsv on vsv.id = rs.value_set_version_id
 where vsvr.code_system = p_code_system;
if l_txt is not null then 
	if force then 
	  select distinct terminology.delete_value_set(vsv.value_set)
	    from terminology.value_set_version_rule vsvr
	         inner join terminology.value_set_version_rule_set rs on rs.id = vsvr.rule_set_id
	         inner join terminology.value_set_version vsv on vsv.id = rs.value_set_version_id;
	else 
	   raise exception 'Dependent value set(s) detected: %', l_txt;
	   return false;
	end if;
end if;

delete from terminology.code_system_association
 where source_code_system_entity_version_id in (
       select id from terminology.code_system_entity_version 
        where code_system_entity_id in (
            select id from terminology.code_system_entity where code_system = p_code_system))
   or target_code_system_entity_version_id in (
      select id from terminology.code_system_entity_version 
       where code_system_entity_id in (
            select id from terminology.code_system_entity where code_system = p_code_system))
   or code_system = p_code_system;

delete from terminology.entity_version_code_system_version_membership
 where code_system_entity_version_id in (
       select id from terminology.code_system_entity_version 
         where code_system_entity_id in (
             select id from terminology.code_system_entity where code_system = p_code_system))
    or code_system_version_id in ( 
             select id from terminology.code_system_version where code_system = p_code_system);

delete from terminology.entity_property_value
 where code_system_entity_version_id in (
       select id from terminology.code_system_entity_version 
        where code_system_entity_id in (
              select id from terminology.code_system_entity where code_system = p_code_system) 
           or code_system = p_code_system);

delete from terminology.designation
 where code_system_entity_version_id in (
        select id from terminology.code_system_entity_version 
         where code_system_entity_id in (
               select id from terminology.code_system_entity where code_system = p_code_system) 
            or code_system = p_code_system);

delete from terminology.code_system_entity_version
 where code_system_entity_id in (
           select id from terminology.code_system_entity where code_system = p_code_system) 
    or code_system = p_code_system;

delete from terminology.concept
 where id in (select id from terminology.code_system_entity where code_system = p_code_system);

delete from terminology.code_system_entity
 where code_system = p_code_system;

update terminology.naming_system
   set code_system = null
 where code_system = p_code_system;

delete from terminology.code_system_version
 where code_system = p_code_system;

delete from terminology.entity_property
 where code_system = p_code_system;

delete from terminology.code_system
 where id = p_code_system;

return true;
end;
$function$
;
