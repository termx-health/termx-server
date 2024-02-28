--liquibase formatted sql

--changeset uam:privilege_resource
drop table if exists uam.privilege_resource;

create table uam.privilege_resource (
    id                  bigint default nextval('core.s_entity') primary key,
    privilege_id        bigint not null,
    resource_type       text not null, --ValueSet, CodeSystem, MapSet, Any (any resource), Admin
    resource_id         text,
    actions             jsonb,

    sys_status          char(1) default 'A' not null,
    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_version 		int  not null,
    constraint privilege_resource_privilege_fk foreign key (privilege_id) references uam.privilege(id)
);
create index privilege_resource_privilege_idx on uam.privilege_resource(privilege_id);

select core.create_table_metadata('uam.privilege_resource');
--rollback drop table if exists uam.privilege_resource;
