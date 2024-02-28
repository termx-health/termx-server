--liquibase formatted sql

--changeset uam:privilege
drop table if exists uam.privilege;
create table uam.privilege (
    id                  bigint default nextval('core.s_entity') primary key,
    code                text not null,
    names               jsonb,

    sys_status          char(1) default 'A' not null,
    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_version 		int  not null
);

select core.create_table_metadata('uam.privilege');
--rollback drop table if exists uam.privilege;
