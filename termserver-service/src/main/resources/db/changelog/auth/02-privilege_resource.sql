--liquibase formatted sql

--changeset kodality:privilege_resource_1
drop table if exists privilege_resource;
create table privilege_resource (
    id                  bigint default nextval('core.s_entity') primary key,
    privilege_id        bigint not null,
    resource_type       text not null, --ValueSet, CodeSystem, MapSet, Any (any resource), Admin
    resource_id         text,
    actions             jsonb,
    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null,
    constraint privilege_resource_privilege_fk foreign key (privilege_id) references privilege(id)
);
create index privilege_resource_privilege_idx on privilege_resource(privilege_id);

select core.create_table_metadata('privilege_resource');
--rollback drop table if exists privilege_resource;
