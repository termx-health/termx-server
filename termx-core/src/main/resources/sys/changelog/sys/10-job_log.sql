--liquibase formatted sql

--changeset kodality:job_log
drop table if exists sys.job_log;
create table sys.job_log (
    id                  bigint default nextval('core.s_entity') primary key,
    started             timestamptz,
    finished            timestamptz,
    type                text not null,
    source              text,
    status              text not null,
    successes           jsonb,
    warnings            jsonb,
    errors              jsonb,
    sys_created_at      timestamp not null,
    sys_created_by      text not null,
    sys_modified_at     timestamp not null,
    sys_modified_by     text not null,
    sys_status 			char(1) default 'A' not null collate "C",
    sys_version 		int  not null
);

select core.create_table_metadata('sys.job_log');
--rollback drop table if exists sys.job_log;
