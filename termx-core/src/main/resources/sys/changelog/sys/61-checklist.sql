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

--changeset kodality:checklist-1
drop table if exists sys.checklist_assertion;
drop table if exists sys.checklist_whitelist;
drop table if exists sys.checklist;

create table sys.checklist (
    id                    bigint default nextval('core.s_entity') primary key,
    rule_id               bigint not null,
    resource_type         text not null,
    resource_id           text not null,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null,
    constraint checklist_checklist_rule_fkey foreign key (rule_id) references sys.checklist_rule (id)
);
create index checklist_checklist_rule_idx on sys.checklist (rule_id);


create table sys.checklist_whitelist (
    id                    bigint default nextval('core.s_entity') primary key,
    checklist_id          bigint not null,
    resource_type         text not null,
    resource_id           text not null,
    resource_name         text,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null,
    constraint checklist_whitelist_checklist_fkey foreign key (checklist_id) references sys.checklist (id)
);
create index checklist_whitelist_checklist_idx on sys.checklist_whitelist (checklist_id);


create table sys.checklist_assertion (
    id                    bigint default nextval('core.s_entity') primary key,
    checklist_id          bigint not null,
    rule_id               bigint not null,
    resource_version      text not null,
    passed                boolean not null default false,
    executor              text not null,
    execution_date        timestamptz not null,
    errors                jsonb,
    sys_status            char(1) default 'A' not null collate "C",
    sys_version           integer not null,
    sys_created_at        timestamptz not null,
    sys_created_by        text not null,
    sys_modified_at       timestamptz not null,
    sys_modified_by       text not null,
    constraint checklist_assertion_checklist_rule_fkey foreign key (rule_id) references sys.checklist_rule (id),
    constraint checklist_assertion_whitelist_checklist_fkey foreign key (checklist_id) references sys.checklist (id)
);
create index checklist_assertion_checklist_rule_idx on sys.checklist_assertion (rule_id);
create index checklist_assertion_checklist_idx on sys.checklist_assertion (checklist_id);


select core.create_table_metadata('sys.checklist');
select core.create_table_metadata('sys.checklist_whitelist');
select core.create_table_metadata('sys.checklist_assertion');
--
