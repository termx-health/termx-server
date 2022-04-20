--liquibase formatted sql

--changeset kodality:import_log
drop table if exists job_log;
create table job_log (
    id                  bigint default nextval('core.s_entity'),
    started             timestamptz,
    finished            timestamptz,
    type                text,
    source              text not null,
    status              text not null,
    warnings            jsonb,
    errors              jsonb,
    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null,
    constraint import_log_pk primary key (id)
);

select core.create_table_metadata('job_log');
--rollback drop table if exists import_log;
