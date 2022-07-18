--liquibase formatted sql

--changeset kodality:value_set
drop table if exists value_set;
create table value_set (
    id                  text                primary key,
    uri                 text                not null,
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
    sys_version         int                 not null,
    constraint value_set_ukey unique (uri)
);
select core.create_table_metadata('value_set');
--rollback drop table if exists value_set;

--changeset kodality:value_set_version
drop table if exists value_set_version;
create table value_set_version (
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
    constraint value_set_version_ukey unique (value_set, version),
    constraint value_set_version_value_set_fk foreign key (value_set) references value_set(id)
);
create index value_set_version_value_set_idx on value_set_version(value_set);

select core.create_table_metadata('value_set_version');
--rollback drop table if exists value_set_version;

--changeset kodality:value_set_version_rule_set
drop table if exists value_set_version_rule_set;
create table value_set_version_rule_set (
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
    constraint value_set_version_rule_set_value_set_version_ukey unique (value_set_version_id),
    constraint value_set_version_rule_set_value_set_version_fk foreign key (value_set_version_id) references value_set_version(id)
);
create index value_set_version_rule_set_value_set_version_idx on value_set_version_rule_set(value_set_version_id);
select core.create_table_metadata('value_set_version_rule_set');
--rollback drop table if exists value_set_version_rule_set;

--changeset kodality:value_set_version_rule
drop table if exists value_set_version_rule;
create table value_set_version_rule (
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
    constraint value_set_version_rule_rule_set_fk foreign key (rule_set_id) references value_set_version_rule_set(id),
    constraint value_set_version_rule_code_system_fk foreign key (code_system) references code_system(id),
    constraint value_set_version_rule_code_system_version_fk foreign key (code_system_version_id) references code_system_version(id),
    constraint value_set_version_rule_value_set_fk foreign key (value_set) references value_set(id),
    constraint value_set_version_rule_value_set_version_fk foreign key (value_set_version_id) references value_set_version(id)
);
create index value_set_version_rule_rule_set_idx on value_set_version_rule(rule_set_id);
create index value_set_version_rule_code_system_idx on value_set_version_rule(code_system);
create index value_set_version_rule_code_system_version_idx on value_set_version_rule(code_system_version_id);
create index value_set_version_rule_value_set_idx on value_set_version_rule(value_set);
create index value_set_version_rule_value_set_version_idx on value_set_version_rule(value_set_version_id);
select core.create_table_metadata('value_set_version_rule');
--rollback drop table if exists value_set_version_rule;

--changeset kodality:value_set_version_concept
drop table if exists value_set_version_concept;
create table value_set_version_concept (
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
    constraint value_set_version_concept_value_set_version_fk foreign key (value_set_version_id) references value_set_version(id)
);
create index value_set_version_concept_value_set_version_idx on value_set_version_concept(value_set_version_id);

select core.create_table_metadata('value_set_version_concept');
--rollback drop table if exists value_set_version_concept;
