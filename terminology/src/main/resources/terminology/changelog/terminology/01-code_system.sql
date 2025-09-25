--liquibase formatted sql

--changeset kodality:code_system
drop table if exists terminology.code_system;
create table terminology.code_system (
    id                  text                primary key,
    uri                 text,
    identifiers         jsonb,
    names               jsonb               not null,
    content             text                not null,
    base_code_system    text,
    contacts            jsonb,
    case_sensitive      text,
    narrative           text,
    description         text,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null,
    constraint code_system_base_code_system_fk foreign key (base_code_system) references terminology.code_system(id)
);
create unique index code_system_ukey on terminology.code_system (uri) where (sys_status = 'A');
create index code_system_base_code_system_idx on terminology.code_system(base_code_system);

select core.create_table_metadata('terminology.code_system');
--rollback drop table if exists terminology.code_system;

--changeset kodality:code_system_version
drop table if exists terminology.code_system_version;
create table terminology.code_system_version (
    id                  bigint      default nextval('core.s_entity') primary key,
    code_system         text                      not null,
    version             text                      not null,
    source              text,
    preferred_language  text,
    supported_languages text[],
    description         text,
    status              text                      not null,
    release_date        timestamp                 not null,
    expiration_date     timestamp,
    created             timestamptz default now() not null,
    sys_created_at      timestamp                 not null,
    sys_created_by      text                      not null,
    sys_modified_at     timestamp                 not null,
    sys_modified_by     text                      not null,
    sys_status          char(1)     default 'A'   not null collate "C",
    sys_version         int                       not null,
    constraint code_system_version_code_system_fk foreign key (code_system) references terminology.code_system(id)
);
create unique index code_system_version_ukey on terminology.code_system_version (code_system, version) where (sys_status = 'A');
create index code_system_version_code_system_idx on terminology.code_system_version(code_system);

select core.create_table_metadata('terminology.code_system_version');
--rollback drop table if exists terminology.code_system_version;

--changeset kodality:code_system-supported_languages
alter table terminology.code_system add column supported_languages text[];
--

--changeset kodality:code_system-sequence
alter table terminology.code_system add column sequence text;
--

--changeset kodality:code_system-columns_refactor
alter table terminology.code_system add column title jsonb;
update terminology.code_system set title = names;
alter table terminology.code_system drop column names;
alter table terminology.code_system alter column title set not null;

alter table terminology.code_system add column description_temp jsonb;
update terminology.code_system set description_temp = jsonb_strip_nulls(jsonb_build_object('en', description));
alter table terminology.code_system drop column description;
alter table terminology.code_system rename column description_temp to description;

alter table terminology.code_system add column publisher text;
update terminology.code_system cs set publisher = csv.source from terminology.code_system_version csv WHERE csv.code_system = cs.id and cs.sys_status = 'A' and csv.sys_status = 'A';
alter table terminology.code_system_version drop column source;

alter table terminology.code_system add column name jsonb;
alter table terminology.code_system add column purpose jsonb;
alter table terminology.code_system add column hierarchy_meaning text;
alter table terminology.code_system add column experimental boolean;

alter table terminology.code_system_version add column description_temp jsonb;
update terminology.code_system_version set description_temp = jsonb_strip_nulls(jsonb_build_object('en', description));
alter table terminology.code_system_version drop column description;
alter table terminology.code_system_version rename column description_temp to description;

--

--changeset kodality:code_system-settings
alter table terminology.code_system add column settings jsonb;
--

--changeset kodality:code_system-case_sensitive-def-value
update terminology.code_system set case_sensitive = 'ci' where case_sensitive is null;
alter table terminology.code_system alter column case_sensitive set not null;
--

--changeset kodality:code_system_version-algorithm
alter table terminology.code_system_version add column algorithm text;
--

--changeset kodality:code_system-copyright
alter table terminology.code_system add column copyright jsonb;
--

--changeset kodality:code_system-name-to-text
alter table terminology.code_system rename name to name_bak;
alter table terminology.code_system add column name text;
update terminology.code_system set name = (select replace(n.value::text, '"'::text, ''::text) from jsonb_each(name_bak::jsonb) n limit 1 );
alter table terminology.code_system drop column name_bak;
--

--changeset kodality:code_system-permissions
alter table terminology.code_system add column permissions jsonb;
--

--changeset kodality:code_system-external_web_source
alter table terminology.code_system add column external_web_source boolean;
--

--changeset kodality:code_system_version-identifiers
alter table terminology.code_system_version add column identifiers jsonb;
--

--changeset kodality:code_system_version-uri
alter table terminology.code_system_version add column uri text;
--

--changeset kodality:code_system-other_title-source_reference-replaces-topic-configuration_attributes-use_context
alter table terminology.code_system add column other_title jsonb;
alter table terminology.code_system add column source_reference text;
alter table terminology.code_system add column replaces text;
alter table terminology.code_system add column topic jsonb;
alter table terminology.code_system add column configuration_attributes jsonb;
alter table terminology.code_system add column use_context jsonb;

alter table terminology.code_system add constraint code_system_replaces_fk foreign key (replaces) references terminology.code_system(id);
create index code_system_replaces_idx on terminology.code_system(replaces);

--

--changeset kodality:code_system_version-base_code_system_version_id
alter table terminology.code_system_version add column base_code_system_version_id bigint;
alter table terminology.code_system_version add constraint code_system_version_base_code_system_version_fk foreign key (base_code_system_version_id) references terminology.code_system_version(id);
create index code_system_version_base_code_system_version_idx on terminology.code_system_version(base_code_system_version_id);
--

--changeset termx:code_system_update_external_web_source
alter table terminology.code_system
drop column if exists external_web_source,
add column external_web_source text;

--rollback  alter table terminology.code_system
--rollback  drop column if exists external_web_source,
--rollback  add column external_web_source boolean;
--
