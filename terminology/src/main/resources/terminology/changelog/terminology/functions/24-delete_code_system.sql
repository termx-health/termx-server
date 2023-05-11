drop function if exists terminology.delete_code_system(p_code_system text);

create or replace function terminology.delete_code_system(p_code_system text)
    returns void
    language sql
as
$function$
delete from terminology.map_set_association
where source_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system))
        or target_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system));

delete from terminology.code_system_association
where source_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system))
        or target_code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system));

delete from terminology.entity_version_code_system_version_membership
where code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system))
        or code_system_version_id in (select id from terminology.code_system_version where code_system = p_code_system);

delete from terminology.entity_property_value
where code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system) or code_system = p_code_system);

delete from terminology.designation
where code_system_entity_version_id in (select id from terminology.code_system_entity_version where code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system) or code_system = p_code_system);

delete from terminology.code_system_entity_version
where code_system_entity_id in (select id from terminology.code_system_entity where code_system = p_code_system) or code_system = p_code_system;

delete from terminology.code_system_association
where id in (select id from terminology.code_system_entity where code_system = p_code_system);

delete from terminology.concept
where id in (select id from terminology.code_system_entity where code_system = p_code_system);

delete from terminology.code_system_entity
where code_system = p_code_system;

delete from terminology.naming_system
where code_system = p_code_system;

delete from terminology.value_set_version_rule
where code_system = p_code_system;

delete from terminology.code_system_version
where code_system = p_code_system;

delete from terminology.entity_property
where code_system = p_code_system;

delete from terminology.code_system
where id = p_code_system;

select terminology.delete_code_system(id) from terminology.code_system where base_code_system = p_code_system;
$function$
;
