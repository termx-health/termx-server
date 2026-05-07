--liquibase formatted sql

--changeset termx:snomed_rf2_upload
create table if not exists sys.snomed_rf2_upload (
    id                          bigserial           not null,
    branch_path                 text                not null,
    rf2_type                    text                not null,
    create_code_system_version  boolean default false,
    filename                    text,
    zip_size                    bigint,
    zip_data                    bytea               not null,
    scan_lorque_id              bigint,
    imported                    boolean             not null default false,
    started                     timestamptz         not null default current_timestamp,
    imported_at                 timestamptz,
    sys_created_at              timestamp           not null,
    sys_created_by              text                not null,
    sys_modified_at             timestamp           not null,
    sys_modified_by             text                not null,
    sys_status                  char(1) default 'A' not null collate "C",
    sys_version                 int                 not null,
    constraint snomed_rf2_upload_pk primary key (id)
);
create index snomed_rf2_upload_lorque_idx on sys.snomed_rf2_upload(scan_lorque_id);
create index snomed_rf2_upload_started_idx on sys.snomed_rf2_upload(started);

select core.create_table_metadata('sys.snomed_rf2_upload');
--rollback drop table if exists sys.snomed_rf2_upload;
--
