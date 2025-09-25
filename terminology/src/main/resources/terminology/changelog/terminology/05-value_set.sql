--liquibase formatted sql

--changeset kodality:value_set
drop table if exists terminology.value_set;
create table terminology.value_set (
    id                  text                primary key,
    uri                 text,
    identifiers         jsonb,
    names               jsonb               not null,
    contacts            jsonb,
    narrative           text,
    description         text,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null
);
create unique index value_set_ukey on terminology.value_set (uri) where (sys_status = 'A');

select core.create_table_metadata('terminology.value_set');
--rollback drop table if exists terminology.value_set;

--changeset kodality:value_set_version
drop table if exists terminology.value_set_version;
create table terminology.value_set_version (
    id                  bigint      default nextval('core.s_entity') primary key,
    value_set           text                      not null,
    version             text                      not null,
    source              text,
    supported_languages text[],
    rule_set            jsonb,
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
    constraint value_set_version_value_set_fk foreign key (value_set) references terminology.value_set(id)
);
create unique index value_set_version_ukey on terminology.value_set_version (value_set, version) where (sys_status = 'A');
create index value_set_version_value_set_idx on terminology.value_set_version(value_set);

select core.create_table_metadata('terminology.value_set_version');
--rollback drop table if exists terminology.value_set_version;

--changeset kodality:value_set_version_rule_set
drop table if exists terminology.value_set_version_rule_set;
create table terminology.value_set_version_rule_set (
    id                   bigint default nextval('core.s_entity') primary key,
    value_set_version_id bigint                 not null,
    locked_date          timestamp,
    inactive             boolean,
    sys_created_at       timestamp              not null,
    sys_created_by       text                   not null,
    sys_modified_at      timestamp              not null,
    sys_modified_by      text                   not null,
    sys_status           char(1) default 'A'    not null collate "C",
    sys_version          int                    not null,
    constraint value_set_version_rule_set_value_set_version_fk foreign key (value_set_version_id) references terminology.value_set_version(id)
);
create unique index value_set_version_rule_set_value_set_version_ukey on terminology.value_set_version_rule_set (value_set_version_id) where (sys_status = 'A');
create index value_set_version_rule_set_value_set_version_idx on terminology.value_set_version_rule_set(value_set_version_id);

select core.create_table_metadata('terminology.value_set_version_rule_set');
--rollback drop table if exists terminology.value_set_version_rule_set;

--changeset kodality:value_set_version_rule
drop table if exists terminology.value_set_version_rule;
create table terminology.value_set_version_rule (
    id                     bigint default nextval('core.s_entity') primary key,
    rule_set_id            bigint               not null,
    type                   text                 not null, --include,exclude
    code_system            text,
    code_system_version_id bigint,
    concepts               jsonb,
    filters                jsonb,
    value_set              text,
    value_set_version_id   bigint,
    sys_created_at         timestamp            not null,
    sys_created_by         text                 not null,
    sys_modified_at        timestamp            not null,
    sys_modified_by        text                 not null,
    sys_status             char(1) default 'A'  not null collate "C",
    sys_version            int                  not null,
    constraint value_set_version_rule_rule_set_fk foreign key (rule_set_id) references terminology.value_set_version_rule_set(id),
    constraint value_set_version_rule_code_system_fk foreign key (code_system) references terminology.code_system(id),
    constraint value_set_version_rule_code_system_version_fk foreign key (code_system_version_id) references terminology.code_system_version(id),
    constraint value_set_version_rule_value_set_fk foreign key (value_set) references terminology.value_set(id),
    constraint value_set_version_rule_value_set_version_fk foreign key (value_set_version_id) references terminology.value_set_version(id)
);
create index value_set_version_rule_rule_set_idx on terminology.value_set_version_rule(rule_set_id);
create index value_set_version_rule_code_system_idx on terminology.value_set_version_rule(code_system);
create index value_set_version_rule_code_system_version_idx on terminology.value_set_version_rule(code_system_version_id);
create index value_set_version_rule_value_set_idx on terminology.value_set_version_rule(value_set);
create index value_set_version_rule_value_set_version_idx on terminology.value_set_version_rule(value_set_version_id);
select core.create_table_metadata('terminology.value_set_version_rule');
--rollback drop table if exists terminology.value_set_version_rule;

--changeset kodality:value_set_version_concept
drop table if exists terminology.value_set_version_concept;
create table terminology.value_set_version_concept (
    id                      bigint default nextval('core.s_entity') primary key,
    value_set_version_id    bigint                  not null,
    concept                 jsonb                   not null,
    display                 jsonb                   not null,
    additional_designations jsonb,
    sys_created_at          timestamp               not null,
    sys_created_by          text                    not null,
    sys_modified_at         timestamp               not null,
    sys_modified_by         text                    not null,
    sys_status              char(1) default 'A'     not null collate "C",
    sys_version             int                     not null,
    constraint value_set_version_concept_value_set_version_fk foreign key (value_set_version_id) references terminology.value_set_version(id)
);
create index value_set_version_concept_value_set_version_idx on terminology.value_set_version_concept(value_set_version_id);

select core.create_table_metadata('terminology.value_set_version_concept');
--rollback drop table if exists terminology.value_set_version_concept;

--changeset kodality:value_set_version_snapshot
drop table if exists terminology.value_set_snapshot;
create table terminology.value_set_snapshot (
    id                      bigserial               not null primary key,
    value_set               text                    not null,
    value_set_version_id    bigint                  not null,
    concepts_total          integer                 not null,
    expansion               jsonb                   not null,
    created_at              timestamptz             not null,
    created_by              text,
    sys_created_at          timestamp               not null,
    sys_created_by          text                    not null,
    sys_modified_at         timestamp               not null,
    sys_modified_by         text                    not null,
    sys_status              char(1) default 'A'     not null collate "C",
    sys_version             int                     not null,
    constraint value_set_snapshot_value_set_fk foreign key (value_set) references terminology.value_set(id),
    constraint value_set_snapshot_value_set_version_fk foreign key (value_set_version_id) references terminology.value_set_version(id)
);
create index value_set_snapshot_value_set_idx on terminology.value_set_snapshot(value_set);
create index value_set_snapshot_value_set_version_idx on terminology.value_set_snapshot(value_set_version_id);
create unique index value_set_snapshot_value_set_version_id ON terminology.value_set_snapshot (value_set_version_id) WHERE sys_status = 'A';

select core.create_table_metadata('terminology.value_set_snapshot');
--rollback drop table if exists terminology.value_set_snapshot;

--changeset kodality:value_set_version_concept-order_number
alter table terminology.value_set_version_concept add column order_number smallint;
--

--changeset kodality:value_set-columns_refactor
alter table terminology.value_set add column title jsonb;
update terminology.value_set set title = names;
alter table terminology.value_set drop column names;
alter table terminology.value_set alter column title set not null;

alter table terminology.value_set add column description_temp jsonb;
update terminology.value_set set description_temp = jsonb_strip_nulls(jsonb_build_object('en', description));
alter table terminology.value_set drop column description;
alter table terminology.value_set rename column description_temp to description;

alter table terminology.value_set add column publisher text;
update terminology.value_set vs set publisher = vsv.source from terminology.value_set_version vsv WHERE vsv.value_set = vs.id and vs.sys_status = 'A' and vsv.sys_status = 'A';
alter table terminology.value_set_version drop column source;

alter table terminology.value_set add column name jsonb;
alter table terminology.value_set add column purpose jsonb;
alter table terminology.value_set add column experimental boolean;

alter table terminology.value_set_version add column description_temp jsonb;
update terminology.value_set_version set description_temp = jsonb_strip_nulls(jsonb_build_object('en', description));
alter table terminology.value_set_version drop column description;
alter table terminology.value_set_version rename column description_temp to description;

alter table terminology.value_set_version add column algorithm text;
--

--changeset kodality:value_set-settings
alter table terminology.value_set add column settings jsonb;
--

--changeset kodality:value_set-copyright
alter table terminology.value_set add column copyright jsonb;
--

--changeset kodality:value_set-permissions
alter table terminology.value_set add column permissions jsonb;
--

--changeset kodality:value_set-name-to-text
alter table terminology.value_set rename name to name_bak;
alter table terminology.value_set add column name text;
update terminology.value_set set name = (select replace(n.value::text, '"'::text, ''::text) from jsonb_each(name_bak::jsonb) n limit 1 );
alter table terminology.value_set drop column name_bak;
--

--changeset kodality:value_set-external_web_source
alter table terminology.value_set add column external_web_source boolean;
--

--changeset kodality:value_set_version-preferred_language
alter table terminology.value_set_version add column preferred_language text;
--

--changeset kodality:value_set_version_rule-properties
alter table terminology.value_set_version_rule add column properties jsonb;
--

--changeset kodality:value_set_version-identifiers
alter table terminology.value_set_version add column identifiers jsonb;
--

--changeset kodality:value_set-other_title-source_reference-replaces-topic-configuration_attributes-use_context
alter table terminology.value_set add column other_title jsonb;
alter table terminology.value_set add column source_reference text;
alter table terminology.value_set add column replaces text;
alter table terminology.value_set add column topic jsonb;
alter table terminology.value_set add column configuration_attributes jsonb;
alter table terminology.value_set add column use_context jsonb;

alter table terminology.value_set add constraint value_set_replaces_fk foreign key (replaces) references terminology.value_set(id);
create index value_set_replaces_idx on terminology.value_set(replaces);
--

--changeset kodality:value_set_version_rule_set-inactive-not_null
update terminology.value_set_version_rule_set set inactive = false where inactive is null;
alter table terminology.value_set_version_rule_set alter column inactive set not null;
--

--changeset termx:value_set_update_external_web_source
alter table terminology.value_set
drop column if exists external_web_source,
add column external_web_source text;

--rollback  alter table terminology.value_set
--rollback  drop column if exists external_web_source,
--rollback  add column external_web_source boolean;
--