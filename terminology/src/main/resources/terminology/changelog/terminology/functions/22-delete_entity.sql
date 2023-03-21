drop function if exists terminology.delete_entity(p_entity_id bigint);

create or replace function terminology.delete_entity(p_entity_id bigint)
    returns void
    language sql
as
$function$
delete from terminology.map_set_association
where source_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id)
        or target_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id);

delete from terminology.code_system_association
where source_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id)
        or target_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id);

delete from terminology.entity_version_code_system_version_membership
where code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id);

delete from terminology.entity_property_value
where code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id);

delete from terminology.designation
where code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id = p_entity_id);

delete from terminology.code_system_entity_version
where code_system_entity_id = p_entity_id;

delete from terminology.code_system_association
where id = p_entity_id;

delete from terminology.concept
where id = p_entity_id;

delete from terminology.code_system_entity
where id = p_entity_id;
$function$
;
