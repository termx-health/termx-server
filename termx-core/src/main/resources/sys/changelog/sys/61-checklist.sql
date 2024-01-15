--liquibase formatted sql

--changeset kodality:checklist_rule
drop table if exists sys.checklist_rule;

create table sys.checklist_rule (
    id                    bigint default nextval('core.s_entity') primary key,
    code                  text not null,
    title                 jsonb not null,
    description           jsonb,
    active                boolean not null default false,
    type                  text not null,
    verification          text not null,
    severity              text not null,
    target                text not null,
    resource_type         text not null,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null
);
create unique index checklist_rule_ukey on sys.checklist_rule (code) where (sys_status = 'A');

select core.create_table_metadata('sys.checklist_rule');
--
