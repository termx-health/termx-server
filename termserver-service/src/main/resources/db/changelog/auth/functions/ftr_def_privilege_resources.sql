create or replace function auth.ftr_def_privilege_resources()
    returns trigger
    language plpgsql
as
$body$
begin
    delete from auth.privilege_resource pr where pr.privilege_id = new.id;

    with cs(code_system) as (values ('publication-status'),
                                 ('codesystem-content-mode'),
                                 ('v3-ietf3066'),
                                 ('concept-property-type'),
                                 ('contact-point-system'),
                                 ('contact-point-use'),
                                 ('filter-operator'),
                                 ('namingsystem-identifier-type'),
                                 ('namingsystem-type'))
    insert
    into auth.privilege_resource (privilege_id, resource_type, resource_id)
    select new.id, 'CodeSystem', cs.code_system
    from cs where new.code like '%.code-system.%';

    with vs(value_set) as (values ('publication-status'),
                                 ('codesystem-content-mode'),
                                 ('languages'),
                                 ('concept-property-type'),
                                 ('contact-point-system'),
                                 ('contact-point-use'),
                                 ('filter-operator'),
                                 ('namingsystem-identifier-type'),
                                 ('namingsystem-type'))
    insert
    into auth.privilege_resource (privilege_id, resource_type, resource_id)
    select new.id, 'ValueSet', vs.value_set
    from vs where new.code like '%.value-set.%';

    return new;

end;
$body$
;
