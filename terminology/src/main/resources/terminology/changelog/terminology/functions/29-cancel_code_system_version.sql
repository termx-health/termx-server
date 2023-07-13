drop function if exists terminology.cancel_code_system_version(p_code_system_version_id bigint);

create or replace function terminology.cancel_code_system_version(p_code_system_version_id bigint)
    returns void
    language sql
as
$function$
update terminology.map_set_association
set sys_status = 'D'
where source_code_system_entity_version_id in (select code_system_entity_version_id from terminology.entity_version_code_system_version_membership where code_system_version_id = p_code_system_version_id)
        or target_code_system_entity_version_id in (select code_system_entity_version_id from terminology.entity_version_code_system_version_membership where code_system_version_id = p_code_system_version_id);

update terminology.code_system_association
set sys_status = 'D'
where source_code_system_entity_version_id in (select code_system_entity_version_id from terminology.entity_version_code_system_version_membership where code_system_version_id = p_code_system_version_id)
        or target_code_system_entity_version_id in (select code_system_entity_version_id from terminology.entity_version_code_system_version_membership where code_system_version_id = p_code_system_version_id);

update terminology.entity_version_code_system_version_membership
set sys_status = 'D'
where code_system_version_id = p_code_system_version_id;

update terminology.code_system_version
set sys_status = 'D'
where id = p_code_system_version_id;
$function$
;
