--liquibase formatted sql

--changeset kodality:terminology_server
drop table if exists sys.terminology_server;

create table sys.terminology_server (
    id                    bigint default nextval('core.s_entity') primary key,
    code                  text not null,
    names                 jsonb not null,
    root_url              text not null,
    active                boolean not null default false,
    current_installation  boolean not null default false,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null
);
create unique index terminology_server_ukey on sys.terminology_server (code) where (sys_status = 'A');

select core.create_table_metadata('sys.terminology_server');
--

--changeset kodality:terminology_server-kind
alter table sys.terminology_server add column kind jsonb;
update sys.terminology_server set kind = '["terminology"]'::jsonb;
alter table sys.terminology_server alter column kind set not null;
--

--changeset kodality:terminology_server-headers
alter table sys.terminology_server add column headers jsonb;
--

--changeset kodality:terminology_server-auth
alter table sys.terminology_server add column auth_config jsonb;
--

--changeset termx:terminology_server-ecosystem_fields
alter table sys.terminology_server add column if not exists access_info text;
alter table sys.terminology_server add column if not exists usage jsonb;
alter table sys.terminology_server add column if not exists authoritative jsonb;
alter table sys.terminology_server add column if not exists authoritative_valuesets jsonb;
alter table sys.terminology_server add column if not exists exclusions jsonb;
alter table sys.terminology_server add column if not exists fhir_versions jsonb;
alter table sys.terminology_server add column if not exists supported_operations jsonb;
--

--changeset termx:terminology_server-ecosystem_fields_v2
alter table sys.terminology_server add column if not exists cache_period_hours integer;
alter table sys.terminology_server add column if not exists strategy text;
alter table sys.terminology_server add column if not exists authoritative_conceptmaps jsonb;
alter table sys.terminology_server add column if not exists authoritative_structuredefinitions jsonb;
alter table sys.terminology_server add column if not exists authoritative_structuremaps jsonb;
alter table sys.terminology_server add column if not exists open boolean;
alter table sys.terminology_server add column if not exists token boolean;
alter table sys.terminology_server add column if not exists oauth_flag boolean;
alter table sys.terminology_server add column if not exists smart_flag boolean;
alter table sys.terminology_server add column if not exists cert_flag boolean;
--rollback alter table sys.terminology_server drop column if exists cache_period_hours, drop column if exists strategy, drop column if exists authoritative_conceptmaps, drop column if exists authoritative_structuredefinitions, drop column if exists authoritative_structuremaps, drop column if exists open, drop column if exists token, drop column if exists oauth_flag, drop column if exists smart_flag, drop column if exists cert_flag;

--changeset termx:terminology_server-root_url_nullable
alter table sys.terminology_server alter column root_url drop not null;
--rollback alter table sys.terminology_server alter column root_url set not null;

