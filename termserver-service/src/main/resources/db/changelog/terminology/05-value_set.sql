--liquibase formatted sql

--changeset kodality:value_set
drop table if exists value_set;
create table value_set (
    id                  text                primary key,
    uri                 text                not null,
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

--changeset kodality:concept_value_set_version_membership
drop table if exists concept_value_set_version_membership;
create table concept_value_set_version_membership (
    id                              bigserial                 not null primary key,
    concept_id                      bigint                    not null,
    value_set_version_id            bigint                    not null,
    display                         bigint,
    additional_designations         bigint[],
    order_nr                        int,
    sys_created_at                  timestamp                 not null,
    sys_created_by                  text                      not null,
    sys_modified_at                 timestamp                 not null,
    sys_modified_by                 text                      not null,
    sys_status                      char(1)     default 'A'   not null collate "C",
    sys_version                     int                       not null,
    constraint concept_value_set_version_membership_concept_fk foreign key (concept_id) references concept(id),
    constraint concept_value_set_version_membership_display_fk foreign key (display) references designation(id),
    constraint concept_value_set_version_membership_value_set_version_fk foreign key (value_set_version_id) references value_set_version(id)
);

select core.create_table_metadata('concept_value_set_version_membership');
--rollback drop table if exists concept_value_set_version_membership;
