drop function if exists terminology.cancel_code_system(p_code_system text);

create or replace function terminology.cancel_code_system(p_code_system text)
    returns void
    language sql
as
$function$
-- TODO. more elegant solution needed than deletion of the rule
update terminology.value_set_version_rule
   set sys_status = 'D'
 where sys_status <> 'D'
   and code_system = p_code_system;

update terminology.code_system_association
   set sys_status = 'D'
 where sys_status <> 'D'
   and (source_code_system_entity_version_id in (
           select id from terminology.code_system_entity_version 
            where code_system_entity_id in (
                select id from terminology.code_system_entity where code_system = p_code_system))
       or target_code_system_entity_version_id in (
           select id from terminology.code_system_entity_version where code_system_entity_id in (
                select id from terminology.code_system_entity where code_system = p_code_system))
       );

update terminology.entity_version_code_system_version_membership
   set sys_status = 'D'
 where sys_status <> 'D'
   and (
       code_system_entity_version_id in (
          select id from terminology.code_system_entity_version where code_system_entity_id in (
            select id from terminology.code_system_entity where code_system = p_code_system))
       or code_system_version_id in (
          select id from terminology.code_system_version where code_system = p_code_system)
       );

update terminology.entity_property_value
   set sys_status = 'D'
 where sys_status <> 'D'
   and code_system_entity_version_id in (
        select id from terminology.code_system_entity_version where code_system_entity_id in (
            select id from terminology.code_system_entity where code_system = p_code_system));

update terminology.designation
   set sys_status = 'D'
 where sys_status <> 'D'
   and code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system));

update terminology.code_system_entity_version
   set sys_status = 'D'
 where sys_status <> 'D'
   and code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system);

update terminology.code_system_association
   set sys_status = 'D'
 where sys_status <> 'D'
   and id in (select id from terminology.code_system_entity where code_system = p_code_system);

update terminology.concept
   set sys_status = 'D'
 where sys_status <> 'D'
   and id in (select id from terminology.code_system_entity where code_system = p_code_system);

update terminology.code_system_entity
   set sys_status = 'D'
 where sys_status <> 'D'
   and code_system = p_code_system;

update terminology.naming_system
   set code_system = null
 where code_system = p_code_system;

update terminology.code_system_version
   set sys_status = 'D'
 where sys_status <> 'D'
   and code_system = p_code_system;

update terminology.entity_property
   set sys_status = 'D'
 where sys_status <> 'D'
   and code_system = p_code_system;

update terminology.code_system
   set sys_status = 'D'
 where sys_status <> 'D'
   and id = p_code_system;

$function$
;
