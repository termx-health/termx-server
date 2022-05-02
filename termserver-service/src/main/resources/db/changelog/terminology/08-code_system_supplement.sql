--liquibase formatted sql

--changeset kodality:code_system_supplement
drop table if exists code_system_supplement;
create table code_system_supplement (
    id                          bigint default nextval('core.s_entity') primary key,
    code_system                 text                        not null,
    property_supplement         bigint,
    property_value_supplement   bigint,
    designation_supplement      bigint,
    type                        text                        not null, --property, property value, designation
    description                 text,
    created                     timestamptz default now()   not null,
    sys_created_at              timestamp                   not null,
    sys_created_by              text                        not null,
    sys_modified_at             timestamp                   not null,
    sys_modified_by             text                        not null,
    sys_status                  char(1) default 'A'         not null collate "C",
    sys_version                 int                         not null,
    constraint code_system_supplement_code_system_fk foreign key (code_system) references code_system(id),
    constraint code_system_supplement_property_supplement_fk foreign key (property_supplement) references entity_property(id),
    constraint code_system_supplement_property_value_supplement_fk foreign key (property_value_supplement) references entity_property_value(id),
    constraint code_system_supplement_designation_supplement_fk foreign key (designation_supplement) references designation(id)
);
create index code_system_supplement_code_system_idx on code_system_supplement(code_system);


select core.create_table_metadata('code_system_supplement');
--rollback drop table if exists code_system_supplement;
