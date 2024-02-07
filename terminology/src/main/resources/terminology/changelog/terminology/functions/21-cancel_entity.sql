drop function if exists terminology.cancel_entity(p_entity_id bigint);
drop function if exists terminology.cancel_entity(p_entity_id bigint, p_code_system text);

create or replace function terminology.cancel_entity(p_entity_id bigint, p_code_system text)
    returns void
    language sql
as
$function$
update terminology.code_system_association
set sys_status = 'D'
where source_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id and code_system = p_code_system)
        or target_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id and code_system = p_code_system);

update terminology.entity_version_code_system_version_membership
set sys_status = 'D'
where code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id and code_system = p_code_system);

update terminology.entity_property_value
set sys_status = 'D'
where code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id and code_system = p_code_system);

update terminology.designation
set sys_status = 'D'
where code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id and code_system = p_code_system);

update terminology.code_system_entity_version
set sys_status = 'D'
where code_system_entity_id = p_entity_id and code_system = p_code_system;

update terminology.code_system_association
set sys_status = 'D'
where id = p_entity_id and code_system = p_code_system;

update terminology.concept
set sys_status = 'D'
where id = p_entity_id and code_system = p_code_system;

update terminology.code_system_entity
set sys_status = 'D'
where id = p_entity_id and code_system = p_code_system;
$function$
;
