--liquibase formatted sql

--changeset kodality:tenant
create table if not exists core.acl (
  s_id bigint not null,
  tenant text collate "C",
  access text not null,
  sys_created_at        timestamp not null,
  sys_created_by        text not null,
  sys_status            char(1) default 'A' not null collate "C"
);
create unique index acl_id_tenant_idx on core.acl(s_id, coalesce(tenant, '--'));

select core.create_table_metadata('core.acl');
--

--changeset kodality:tenant-uidx
drop index core.acl_id_tenant_idx;
create unique index acl_uidx on core.acl(s_id, coalesce(tenant, '--')) where sys_status = 'A';
--
